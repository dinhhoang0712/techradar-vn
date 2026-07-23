import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import StatCard from './StatCard';

describe('StatCard', () => {
    it('renders icon, label, value and caption', () => {
        render(<StatCard icon="📝" label="Tổng bài viết" value={128} caption="+12 hôm nay" />);

        expect(screen.getByText('📝')).toBeInTheDocument();
        expect(screen.getByText('Tổng bài viết')).toBeInTheDocument();
        expect(screen.getByText('128')).toBeInTheDocument();
        expect(screen.getByText('+12 hôm nay')).toBeInTheDocument();
    });

    it('renders as a plain (non-interactive) card by default', () => {
        render(<StatCard label="Tổng user" value={10} />);
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('renders as a clickable button and fires onClick, when onClick is provided', async () => {
        const user = userEvent.setup();
        const onClick = vi.fn();
        render(<StatCard label="Báo cáo chờ duyệt" value={3} onClick={onClick} />);

        const button = screen.getByRole('button', { name: /Báo cáo chờ duyệt/ });
        await user.click(button);
        expect(onClick).toHaveBeenCalledTimes(1);
    });

    it('applies the requested accent class, defaulting to primary', () => {
        const { container, rerender } = render(<StatCard label="A" value={1} />);
        expect(container.firstChild).toHaveClass('stat-card--accent-primary');

        rerender(<StatCard label="A" value={1} accent="danger" />);
        expect(container.firstChild).toHaveClass('stat-card--accent-danger');
    });
});
