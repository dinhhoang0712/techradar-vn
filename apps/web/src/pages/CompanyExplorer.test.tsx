import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import CompanyExplorer from './CompanyExplorer';
import { getCompanies } from '../api/companyService';
import type { Company } from '../types/company';
import type { ApiResponse } from '../types/api';

vi.mock('../api/companyService', () => ({
    getCompanies: vi.fn(),
    getSimilarCompanies: vi.fn(),
}));

const mockedGetCompanies = vi.mocked(getCompanies);

const PAGE_SIZE = 24;

function makeCompanies(count: number, offset = 0): Company[] {
    return Array.from({ length: count }, (_, i) => ({
        id: `c${offset + i}`,
        name: `Company ${offset + i}`,
        job_count: 5,
        tech_stack: ['React'],
    }));
}

// jsdom has no IntersectionObserver; this fake captures the callback so tests can simulate the
// scroll sentinel entering the viewport by calling `.trigger(true)` on the latest instance.
class FakeIntersectionObserver {
    static instances: FakeIntersectionObserver[] = [];
    callback: IntersectionObserverCallback;
    observe = vi.fn();
    disconnect = vi.fn();
    unobserve = vi.fn();

    constructor(callback: IntersectionObserverCallback) {
        this.callback = callback;
        FakeIntersectionObserver.instances.push(this);
    }

    trigger(isIntersecting: boolean) {
        this.callback([{ isIntersecting } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
    }
}

function latestObserver(): FakeIntersectionObserver {
    const instance = FakeIntersectionObserver.instances.at(-1);
    if (!instance) throw new Error('No IntersectionObserver was created');
    return instance;
}

describe('CompanyExplorer infinite scroll + backpressure', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        FakeIntersectionObserver.instances = [];
        vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    });

    it('renders the first page and starts observing the scroll sentinel', async () => {
        mockedGetCompanies.mockResolvedValueOnce({ data: makeCompanies(PAGE_SIZE) } as never);

        render(<CompanyExplorer />);

        expect(await screen.findByText('Company 0')).toBeInTheDocument();
        expect(screen.getByText('Company 23')).toBeInTheDocument();
        expect(mockedGetCompanies).toHaveBeenCalledTimes(1);
        expect(mockedGetCompanies).toHaveBeenCalledWith({ q: undefined, page: 0, size: PAGE_SIZE });
        expect(latestObserver().observe).toHaveBeenCalledTimes(1);
    });

    it('appends (not replaces) the next page once the sentinel intersects', async () => {
        mockedGetCompanies.mockResolvedValueOnce({ data: makeCompanies(PAGE_SIZE) } as never);
        render(<CompanyExplorer />);
        await screen.findByText('Company 0');

        mockedGetCompanies.mockResolvedValueOnce({ data: makeCompanies(10, PAGE_SIZE) } as never);
        latestObserver().trigger(true);

        expect(await screen.findByText('Company 33')).toBeInTheDocument();
        expect(screen.getByText('Company 0')).toBeInTheDocument(); // first page still there — appended, not replaced
        expect(mockedGetCompanies).toHaveBeenLastCalledWith({ q: undefined, page: 1, size: PAGE_SIZE });
        // last page was short (10 < PAGE_SIZE) — nothing left to page in
        expect(await screen.findByText('Đã hiển thị toàn bộ công ty phù hợp.')).toBeInTheDocument();
    });

    it('backpressure: a sentinel re-trigger while a page request is in flight does not fire a second request', async () => {
        mockedGetCompanies.mockResolvedValueOnce({ data: makeCompanies(PAGE_SIZE) } as never);
        render(<CompanyExplorer />);
        await screen.findByText('Company 0');

        let resolveNextPage!: (value: ApiResponse<Company[]>) => void;
        mockedGetCompanies.mockReturnValueOnce(new Promise((resolve) => { resolveNextPage = resolve; }));

        const observer = latestObserver();
        observer.trigger(true); // fires the page-1 request
        observer.trigger(true); // would fire a duplicate page-1 request without the loadingRef guard
        observer.trigger(true);

        await waitFor(() => expect(mockedGetCompanies).toHaveBeenCalledTimes(2)); // page 0 + exactly one page 1

        resolveNextPage({ data: makeCompanies(PAGE_SIZE, PAGE_SIZE) });
        expect(await screen.findByText('Company 47')).toBeInTheDocument();
        expect(mockedGetCompanies).toHaveBeenCalledTimes(2);
    });

    it('stops requesting once the backend reports no more pages', async () => {
        mockedGetCompanies.mockResolvedValueOnce({ data: makeCompanies(10) } as never); // short page 0 → hasMore=false
        render(<CompanyExplorer />);
        await screen.findByText('Đã hiển thị toàn bộ công ty phù hợp.');

        latestObserver().trigger(true);
        latestObserver().trigger(true);

        await new Promise((r) => setTimeout(r, 0));
        expect(mockedGetCompanies).toHaveBeenCalledTimes(1); // still just the initial page 0 call
    });
});
