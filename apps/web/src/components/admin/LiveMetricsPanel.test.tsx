import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LiveMetricsPanel from './LiveMetricsPanel';
import { streamAdminLiveMetrics } from '../../api/adminService';
import type { AdminLiveMetrics } from '../../types/admin';

vi.mock('../../api/adminService', () => ({
    streamAdminLiveMetrics: vi.fn(),
}));

// Job-failure tile polls this independently of the SSE snapshot (see LiveMetricsPanel's second
// effect) — mocked so it resolves predictably instead of hitting a real (relative-URL) fetch that
// jsdom can't resolve.
vi.mock('../../api/notificationService', () => ({
    getUnreadCount: vi.fn().mockResolvedValue(0),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return { ...actual, useNavigate: () => mockNavigate };
});

const mockedStream = vi.mocked(streamAdminLiveMetrics);

function baseSnapshot(overrides: Partial<AdminLiveMetrics> = {}): AdminLiveMetrics {
    return {
        crawler: { state: 'idle' },
        radar: { state: 'idle' },
        new_technologies_this_month: 0,
        ai_requests_today: 0,
        pending_reports: 0,
        pipeline_health: { articles_processed: 0, jobs_processed: 0, articles_failed: 0, jobs_failed: 0 },
        ...overrides,
    };
}

describe('LiveMetricsPanel', () => {
    let liveHandler: (metrics: AdminLiveMetrics) => void;
    let errorHandler: ((err: Error) => void) | undefined;
    let abortFn: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.clearAllMocks();
        abortFn = vi.fn();
        mockedStream.mockImplementation((onSnapshot, onError) => {
            liveHandler = onSnapshot;
            errorHandler = onError;
            return { abort: abortFn } as unknown as AbortController;
        });
    });

    it('shows placeholders and a connecting badge before the first snapshot arrives', () => {
        render(<LiveMetricsPanel />);

        expect(screen.getByText('Đang kết nối...')).toBeInTheDocument();
        expect(screen.getAllByText('—')).toHaveLength(5); // crawler, new-tech, ai-requests, pending-reports, job-failure counts
        expect(screen.getByText('Ổn định')).toBeInTheDocument(); // pipeline defaults to healthy until proven otherwise
    });

    it('renders all metrics and flips to LIVE once the first snapshot arrives', () => {
        render(<LiveMetricsPanel />);

        act(() => liveHandler(baseSnapshot({
            crawler: { state: 'idle', finished_at: '2026-07-21T02:00:00Z', success_count: 42, total: 50 },
            radar: { state: 'idle', finished_at: '2026-07-21T03:00:00Z', rows_upserted: 120 },
            new_technologies_this_month: 5,
            ai_requests_today: 128,
            pending_reports: 3,
        })));

        expect(screen.getByText('LIVE')).toBeInTheDocument();
        expect(screen.getByText('42/50')).toBeInTheDocument();
        expect(screen.getByText('5')).toBeInTheDocument();
        expect(screen.getByText('128')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getAllByText('Rảnh')).toHaveLength(2); // crawler pill + radar pill, both idle
        expect(screen.getByText('Ổn định')).toBeInTheDocument();
    });

    it('shows a pulsing "Đang xử lý" state and no last-run caption while the radar rebuild is running', () => {
        render(<LiveMetricsPanel />);

        act(() => liveHandler(baseSnapshot({
            crawler: { state: 'running' },
            radar: { state: 'running', started_at: '2026-07-21T04:00:00Z', finished_at: null, rows_upserted: null },
            ai_requests_today: 3,
        })));

        expect(screen.getAllByText('Đang xử lý')).toHaveLength(2);
        expect(screen.getByText('Chưa từng chạy')).toBeInTheDocument();
    });

    it('highlights the pending-reports tile and navigates to /admin/reports on click', async () => {
        const user = userEvent.setup();
        render(<LiveMetricsPanel />);

        act(() => liveHandler(baseSnapshot({ pending_reports: 2 })));

        expect(screen.getByText('Nhấn để xem và xử lý →')).toBeInTheDocument();
        await user.click(screen.getByText('2'));
        expect(mockNavigate).toHaveBeenCalledWith('/admin/reports');
    });

    it('shows the pipeline as errored (with a pulsing red pill) and calls onOpenPipelineTab on click', async () => {
        const user = userEvent.setup();
        const onOpenPipelineTab = vi.fn();
        render(<LiveMetricsPanel onOpenPipelineTab={onOpenPipelineTab} />);

        act(() => liveHandler(baseSnapshot({
            pipeline_health: {
                articles_processed: 10, jobs_processed: 5, articles_failed: 2, jobs_failed: 1,
                last_failure_at: '2026-07-21T05:00:00Z', last_failure_message: 'Neo4j timeout',
            },
        })));

        expect(screen.getByText('Có lỗi')).toBeInTheDocument();
        expect(screen.getByText(/Lỗi gần nhất/)).toBeInTheDocument();
        await user.click(screen.getByText('Có lỗi'));
        expect(onOpenPipelineTab).toHaveBeenCalledTimes(1);
    });

    it('drops back to the connecting badge when the stream errors', () => {
        render(<LiveMetricsPanel />);
        act(() => liveHandler(baseSnapshot()));
        expect(screen.getByText('LIVE')).toBeInTheDocument();

        act(() => errorHandler?.(new Error('SSE 401')));
        expect(screen.getByText('Đang kết nối...')).toBeInTheDocument();
    });

    it('closes the SSE connection on unmount', () => {
        const { unmount } = render(<LiveMetricsPanel />);
        unmount();
        expect(abortFn).toHaveBeenCalledTimes(1);
    });
});
