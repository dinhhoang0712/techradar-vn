import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SocialTab from './SocialTab';
import type { SocialDashboard } from '../../types/admin';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual('react-router-dom');
    return { ...actual, useNavigate: () => mockNavigate };
});

function baseSocial(overrides: Partial<SocialDashboard> = {}): SocialDashboard {
    return {
        total_posts: 100,
        posts_today: 5,
        total_comments: 40,
        total_likes: 200,
        total_follows: 30,
        top_posters: [],
        ...overrides,
    };
}

describe('SocialTab', () => {
    it('renders every stat card value', () => {
        render(<SocialTab social={baseSocial()} />);

        expect(screen.getByText('100')).toBeInTheDocument();
        expect(screen.getByText('5')).toBeInTheDocument();
        expect(screen.getByText('40')).toBeInTheDocument();
        expect(screen.getByText('200')).toBeInTheDocument();
        expect(screen.getByText('30')).toBeInTheDocument();
    });

    it('shows the pending-reports card as a quiet 0 when there is nothing to review', () => {
        render(<SocialTab social={baseSocial({ pending_reports: 0 })} />);
        expect(screen.getByText('Không có báo cáo nào chờ duyệt')).toBeInTheDocument();
    });

    it('highlights pending reports and navigates to /admin/reports on click', async () => {
        const user = userEvent.setup();
        render(<SocialTab social={baseSocial({ pending_reports: 7 })} />);

        expect(screen.getByText('7')).toBeInTheDocument();
        expect(screen.getByText('Nhấn để xem và xử lý →')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Báo cáo chờ duyệt/ }));
        expect(mockNavigate).toHaveBeenCalledWith('/admin/reports');
    });

    it('shows an empty-state message when there are no top posters', () => {
        render(<SocialTab social={baseSocial({ top_posters: [] })} />);
        expect(screen.getByText('Chưa có dữ liệu')).toBeInTheDocument();
    });
});
