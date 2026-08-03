import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ToastProvider } from '../components/common/ToastProvider';
import TrendDashboard from './TrendDashboard';
import { getRadarTop4, getRadarTop10, getRadarSearch, streamRadar } from '../api/trendService';
import { getForecast } from '../api/forecastService';
import type { RadarSnapshotEvent } from '../types/trend';

vi.mock('../api/trendService', () => ({
    getRadarTop4: vi.fn(),
    getRadarTop10: vi.fn(),
    getRadarSearch: vi.fn(),
    streamRadar: vi.fn(),
}));
vi.mock('../api/forecastService', () => ({ getForecast: vi.fn() }));
vi.mock('../api/summarizeService', () => ({ summarizeTech: vi.fn() }));

const mockedGetRadarTop4 = vi.mocked(getRadarTop4);
const mockedGetRadarTop10 = vi.mocked(getRadarTop10);
const mockedGetRadarSearch = vi.mocked(getRadarSearch);
const mockedStreamRadar = vi.mocked(streamRadar);
const mockedGetForecast = vi.mocked(getForecast);

describe('TrendDashboard live radar stream', () => {
    let liveHandler: (snapshot: RadarSnapshotEvent) => void;
    let abortFn: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.clearAllMocks();
        abortFn = vi.fn();
        mockedStreamRadar.mockImplementation((onSnapshot) => {
            liveHandler = onSnapshot;
            return { abort: abortFn } as unknown as AbortController;
        });
        mockedGetRadarSearch.mockResolvedValue({ data: [] });
    });

    function mockInitialLoad() {
        mockedGetRadarTop4.mockResolvedValueOnce({
            data: [{ industry: 'Kotlin', growth_rate: 45, mom_rate: 32, job_count: 120, jobs_this_month: 40 }],
        } as never);
        mockedGetRadarTop10.mockResolvedValueOnce({
            data: [{ keyword: 'Kotlin', job_count: 120 }],
        } as never);
    }

    it('renders the initial REST snapshot from /radar/top4 and /radar/top10', async () => {
        mockInitialLoad();
        render(<ToastProvider><TrendDashboard /></ToastProvider>);

        expect(await screen.findByText('+45.00%')).toBeInTheDocument();
        expect(screen.getAllByText('Kotlin').length).toBeGreaterThan(0);
        expect(mockedStreamRadar).toHaveBeenCalledTimes(1);
    });

    it('overrides top4/top10 numbers as soon as a live snapshot arrives, without a new REST fetch', async () => {
        mockInitialLoad();
        render(<ToastProvider><TrendDashboard /></ToastProvider>);
        await screen.findByText('+45.00%');
        expect(mockedGetRadarTop4).toHaveBeenCalledTimes(1);

        liveHandler({
            top4: [{ industry: 'Kotlin', growth_rate: 61.5, mom_rate: 50, job_count: 150, jobs_this_month: 55 }],
            top10: [{ keyword: 'Kotlin', job_count: 150 }],
        });

        expect(await screen.findByText('+61.50%')).toBeInTheDocument();
        // still just the one initial REST call — the update came purely from the live stream
        expect(mockedGetRadarTop4).toHaveBeenCalledTimes(1);
    });

    it('closes the SSE connection on unmount', async () => {
        mockInitialLoad();
        const { unmount } = render(<ToastProvider><TrendDashboard /></ToastProvider>);
        await screen.findByText('+45.00%');

        unmount();
        expect(abortFn).toHaveBeenCalledTimes(1);
    });

    it('mở sẵn panel dự báo khi URL có ?forecastTech= (deep-link từ ReportPage)', async () => {
        mockInitialLoad();
        mockedGetForecast.mockResolvedValueOnce({
            data: { technology: 'Kotlin', predicted_direction: 'growing', confidence: 0.8, reasoning: '', signals: [], trend_data: [], current_status: {} },
        } as never);
        window.history.pushState({}, '', '/dashboard?forecastTech=Kotlin');

        render(<ToastProvider><TrendDashboard /></ToastProvider>);

        expect(await screen.findByText('↑ Tăng trưởng')).toBeInTheDocument();
        expect(document.querySelector('.forecast-panel')?.textContent).toContain('Kotlin');
        expect(mockedGetForecast).toHaveBeenCalledWith('Kotlin', 6);

        window.history.pushState({}, '', '/');
    });
});
