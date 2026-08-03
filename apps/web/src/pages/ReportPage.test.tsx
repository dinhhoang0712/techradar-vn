import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ReportPage from './ReportPage';
import { generateReport } from '../api/reportService';
import type { ReportResult } from '../types/report';

vi.mock('../api/reportService', () => ({ generateReport: vi.fn() }));

const mockedGenerateReport = vi.mocked(generateReport);

const RESULT_A: ReportResult = {
    period: '2024-Q4',
    generated_at: '2024-12-31T00:00:00Z',
    report: '# Báo cáo A\n\nNội dung A',
    top_techs: [
        { name: 'React', source: 'analytics', job_count: 120, growth_rate: 15.5, cluster_label: 'Frontend' },
        { name: 'Rust', source: 'analytics', job_count: 200, growth_rate: -5.2, cluster_label: 'Systems' },
        { name: 'Svelte', source: 'articles', mention_count: 12, cluster_label: 'Frontend' },
    ],
};

const RESULT_B: ReportResult = {
    period: '2024-Q3',
    generated_at: '2024-09-30T00:00:00Z',
    report: '# Báo cáo B',
    top_techs: [
        { name: 'React', source: 'analytics', job_count: 90, growth_rate: 10, cluster_label: 'Frontend' },
        { name: 'Vue', source: 'analytics', job_count: 30, growth_rate: 2, cluster_label: 'Frontend' },
    ],
};

function renderPage(initialEntry = '/report') {
    return render(
        <MemoryRouter initialEntries={[initialEntry]}>
            <ReportPage />
        </MemoryRouter>,
    );
}

function setPeriodInput(value: string) {
    fireEvent.change(screen.getByPlaceholderText('VD: 2024-Q4, 2024-12, 2024'), { target: { value } });
}

describe('ReportPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
    });

    it('hiển thị nguồn dữ liệu + lượt nhắc cho công nghệ chỉ có dữ liệu từ bài viết', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        renderPage();

        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));

        expect(await screen.findByText('120 việc làm')).toBeInTheDocument();
        expect(screen.getByText('12 bài viết')).toBeInTheDocument();
        // Svelte chỉ có mention_count → không có growth_rate, hiển thị dấu gạch ngang
        const svelteRow = screen.getByText('Svelte').closest('.report-table-row');
        expect(within(svelteRow as HTMLElement).getByText('—')).toBeInTheDocument();
    });

    it('sắp xếp lại bảng theo Chỉ số khi bấm vào header', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        renderPage();
        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));
        await screen.findByText('120 việc làm');

        const readOrder = () => screen.getAllByText(/^(React|Rust|Svelte)$/).map(el => el.textContent?.replace('📈', ''));

        expect(readOrder()).toEqual(['React', 'Rust', 'Svelte']);

        fireEvent.click(screen.getByRole('button', { name: /Chỉ số/ }));

        const rowsAfter = readOrder();
        // Rust có job_count (200) cao hơn React (120) — sort desc theo Chỉ số phải đưa Rust lên đầu
        expect(rowsAfter).toEqual(['Rust', 'React', 'Svelte']);
    });

    it('lọc bảng theo cluster', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        renderPage();
        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));
        await screen.findByText('120 việc làm');

        fireEvent.change(screen.getByLabelText('Lọc theo cluster'), { target: { value: 'Frontend' } });

        expect(screen.getByText('React')).toBeInTheDocument();
        expect(screen.getByText('Svelte')).toBeInTheDocument();
        expect(screen.queryByText('Rust')).not.toBeInTheDocument();
    });

    it('xuất CSV chứa đúng dữ liệu top techs đang hiển thị', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
        vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

        renderPage();
        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));
        await screen.findByText('120 việc làm');

        fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }));

        expect(createObjectURLSpy).toHaveBeenCalledTimes(1);
        const blob = createObjectURLSpy.mock.calls[0][0] as Blob;
        const text = await blob.text();
        expect(text).toContain('React');
        expect(text).toContain('Svelte');
    });

    it('so sánh 2 kỳ: gọi API cho cả 2 kỳ và hiển thị công nghệ mới/rớt khỏi top', async () => {
        mockedGenerateReport
            .mockResolvedValueOnce({ data: RESULT_A } as never)
            .mockResolvedValueOnce({ data: RESULT_B } as never);
        renderPage();

        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByLabelText('So sánh với kỳ khác'));
        fireEvent.change(screen.getByPlaceholderText('Kỳ so sánh, VD: 2024-Q3'), { target: { value: '2024-Q3' } });
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));

        expect(await screen.findByText('So sánh 2024-Q4 với 2024-Q3')).toBeInTheDocument();
        expect(mockedGenerateReport).toHaveBeenCalledTimes(2);
        expect(mockedGenerateReport).toHaveBeenNthCalledWith(1, '2024-Q4', 10, 'markdown');
        expect(mockedGenerateReport).toHaveBeenNthCalledWith(2, '2024-Q3', 10, 'markdown');

        // Vue chỉ có ở kỳ so sánh (mới lọt top), Rust+Svelte chỉ có ở kỳ hiện tại (rớt khỏi top)
        expect(screen.getByText('Mới lọt Top ở 2024-Q3')).toBeInTheDocument();
        expect(screen.getByText('Rớt khỏi Top ở 2024-Q3')).toBeInTheDocument();
        const strip = screen.getByText('Mới lọt Top ở 2024-Q3').closest('.compare-delta-strip') as HTMLElement;
        expect(within(strip).getByText('Vue')).toBeInTheDocument();
        expect(within(strip).getByText('Rust')).toBeInTheDocument();
        expect(within(strip).getByText('Svelte')).toBeInTheDocument();
    });

    it('lưu lịch sử báo cáo và cho phép xem lại mà không gọi lại API', async () => {
        mockedGenerateReport
            .mockResolvedValueOnce({ data: RESULT_A } as never)
            .mockResolvedValueOnce({ data: RESULT_B } as never);
        renderPage();

        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));
        await screen.findByText('120 việc làm');

        setPeriodInput('2024-Q3');
        fireEvent.click(screen.getByRole('button', { name: 'Tạo báo cáo' }));
        await screen.findByText('Vue');

        expect(mockedGenerateReport).toHaveBeenCalledTimes(2);

        fireEvent.click(screen.getByRole('button', { name: /Lịch sử \(2\)/ }));
        fireEvent.click(screen.getByText('2024-Q4 · Top 10'));

        expect(await screen.findByText('120 việc làm')).toBeInTheDocument();
        // Restore từ lịch sử không được gọi lại API
        expect(mockedGenerateReport).toHaveBeenCalledTimes(2);
    });

    it('sao chép liên kết chia sẻ kèm period/topN/autorun', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
        renderPage();

        setPeriodInput('2024-Q4');
        fireEvent.click(screen.getByRole('button', { name: 'Sao chép liên kết' }));

        expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
            expect.stringMatching(/period=2024-Q4.*topN=10.*autorun=1/),
        );
    });

    it('tự động tạo báo cáo khi mở link chia sẻ có ?period=&topN=&autorun=1', async () => {
        mockedGenerateReport.mockResolvedValueOnce({ data: RESULT_A } as never);
        renderPage('/report?period=2024-Q4&topN=5&autorun=1');

        expect(await screen.findByText('120 việc làm')).toBeInTheDocument();
        expect(mockedGenerateReport).toHaveBeenCalledWith('2024-Q4', 5, 'markdown');
    });
});
