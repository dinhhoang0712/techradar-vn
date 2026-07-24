import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminKgReview from './AdminKgReview';
import {
    fetchTechAliasReviewQueue, approveTechAlias, rejectTechAlias,
    fetchCompanyDuplicates, mergeCompanyDuplicate,
} from '../../api/adminService';
import type { TechAliasReviewItem, CompanyDuplicateGroup } from '../../api/adminService';

vi.mock('../../api/adminService', () => ({
    fetchTechAliasReviewQueue: vi.fn(),
    approveTechAlias: vi.fn(),
    rejectTechAlias: vi.fn(),
    fetchCompanyDuplicates: vi.fn(),
    mergeCompanyDuplicate: vi.fn(),
    KG_REVIEW_CHANGED_EVENT: 'admin-kg-review-changed',
}));

vi.mock('../../components/common/toastContext', () => ({
    useToast: () => vi.fn(),
}));

const mockedFetchQueue = vi.mocked(fetchTechAliasReviewQueue);
const mockedApprove = vi.mocked(approveTechAlias);
const mockedReject = vi.mocked(rejectTechAlias);
const mockedFetchDuplicates = vi.mocked(fetchCompanyDuplicates);
const mockedMerge = vi.mocked(mergeCompanyDuplicate);

function item(overrides: Partial<TechAliasReviewItem> = {}): TechAliasReviewItem {
    return {
        id: 1,
        name_a: 'Golang',
        name_b: 'Go',
        llm_reasoning: 'Cùng là 1 ngôn ngữ, chỉ khác cách viết',
        status: 'pending',
        created_at: '2026-07-24T10:00:00Z',
        ...overrides,
    };
}

function group(overrides: Partial<CompanyDuplicateGroup> = {}): CompanyDuplicateGroup {
    return {
        normalized_core: 'fpt software',
        companies: [
            { id: 'fpt-software', name: 'FPT Software' },
            { id: 'fpt-corp', name: 'Công Ty Cổ Phần Viễn Thông FPT Software' },
        ],
        ...overrides,
    };
}

describe('AdminKgReview', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockedFetchQueue.mockResolvedValue({ data: [] } as never);
        mockedFetchDuplicates.mockResolvedValue({ data: [] } as never);
    });

    it('renders pending Technology alias pairs with the LLM reasoning', async () => {
        mockedFetchQueue.mockResolvedValueOnce({ data: [item()] } as never);

        render(<AdminKgReview />);

        expect(await screen.findByText('Golang')).toBeInTheDocument();
        expect(screen.getByText('Go')).toBeInTheDocument();
        expect(screen.getByText(/Cùng là 1 ngôn ngữ/)).toBeInTheDocument();
    });

    it('shows the empty state once the queue has nothing pending', async () => {
        render(<AdminKgReview />);

        expect(await screen.findByText('Không có alias nào đang chờ duyệt')).toBeInTheDocument();
    });

    it('approving a pair merges the duplicate into the LLM-suggested canonical (name_b) by default', async () => {
        const user = userEvent.setup();
        mockedFetchQueue.mockResolvedValueOnce({ data: [item()] } as never);
        mockedApprove.mockResolvedValueOnce({} as never);

        render(<AdminKgReview />);
        await screen.findByText('Golang');

        await user.click(screen.getByRole('button', { name: 'Duyệt gộp' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận gộp' }));

        await waitFor(() => expect(mockedApprove).toHaveBeenCalledWith(1, 'Go'));
        await waitFor(() => expect(screen.queryByText('Golang')).not.toBeInTheDocument());
    });

    it('picking the other name flips the canonical direction before approving', async () => {
        const user = userEvent.setup();
        mockedFetchQueue.mockResolvedValueOnce({ data: [item()] } as never);
        mockedApprove.mockResolvedValueOnce({} as never);

        render(<AdminKgReview />);
        await screen.findByText('Golang');

        // Select "Golang" as canonical instead of the AI-suggested "Go".
        await user.click(screen.getByRole('button', { name: 'Golang' }));
        await user.click(screen.getByRole('button', { name: 'Duyệt gộp' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận gộp' }));

        await waitFor(() => expect(mockedApprove).toHaveBeenCalledWith(1, 'Golang'));
    });

    it('rejecting a pair keeps both names and removes the item from the queue', async () => {
        const user = userEvent.setup();
        mockedFetchQueue.mockResolvedValueOnce({ data: [item()] } as never);
        mockedReject.mockResolvedValueOnce({} as never);

        render(<AdminKgReview />);
        await screen.findByText('Golang');

        await user.click(screen.getByRole('button', { name: 'Từ chối' }));
        await user.click(screen.getByRole('button', { name: 'Từ chối gộp' }));

        await waitFor(() => expect(mockedReject).toHaveBeenCalledWith(1));
        await waitFor(() => expect(screen.queryByText('Golang')).not.toBeInTheDocument());
    });

    it('switching to the Company tab lazily detects near-duplicate groups', async () => {
        const user = userEvent.setup();
        mockedFetchDuplicates.mockResolvedValueOnce({ data: [group()] } as never);

        render(<AdminKgReview />);
        await screen.findByText('Không có alias nào đang chờ duyệt');
        expect(mockedFetchDuplicates).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', { name: /Công ty nghi trùng/ }));

        expect(await screen.findByText('FPT Software')).toBeInTheDocument();
        expect(screen.getByText('Công Ty Cổ Phần Viễn Thông FPT Software')).toBeInTheDocument();
        expect(mockedFetchDuplicates).toHaveBeenCalledTimes(1);
    });

    it('merging a company group merges every non-canonical company into the selected one', async () => {
        const user = userEvent.setup();
        mockedFetchDuplicates.mockResolvedValueOnce({ data: [group()] } as never);
        mockedMerge.mockResolvedValueOnce({} as never);

        render(<AdminKgReview />);
        await user.click(screen.getByRole('button', { name: /Công ty nghi trùng/ }));
        await screen.findByText('FPT Software');

        await user.click(screen.getByRole('button', { name: 'Gộp nhóm này' }));
        await user.click(screen.getByRole('button', { name: 'Xác nhận gộp' }));

        await waitFor(() => expect(mockedMerge).toHaveBeenCalledWith('fpt-corp', 'fpt-software'));
        await waitFor(() => expect(screen.queryByText('FPT Software')).not.toBeInTheDocument());
    });
});
