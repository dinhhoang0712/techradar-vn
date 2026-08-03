import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import MessageBubble from './MessageBubble';
import type { DirectMessage } from '../../types/messaging';

function baseMessage(overrides: Partial<DirectMessage> = {}): DirectMessage {
    return {
        id: 'm1',
        conversation_id: 'c1',
        sender_id: 'u1',
        content: 'Xin chào',
        created_at: new Date().toISOString(),
        read: false,
        reactions: [],
        ...overrides,
    };
}

describe('MessageBubble', () => {
    it('renders the message text', () => {
        render(<MessageBubble message={baseMessage()} own={false} showSeen={false} onReact={vi.fn()} onRemoveReaction={vi.fn()} />);
        expect(screen.getByText('Xin chào')).toBeInTheDocument();
    });

    it('shows "Đã xem" only when showSeen is true', () => {
        const { rerender } = render(
            <MessageBubble message={baseMessage()} own showSeen={false} onReact={vi.fn()} onRemoveReaction={vi.fn()} />,
        );
        expect(screen.queryByText('Đã xem')).not.toBeInTheDocument();

        rerender(<MessageBubble message={baseMessage()} own showSeen onReact={vi.fn()} onRemoveReaction={vi.fn()} />);
        expect(screen.getByText('Đã xem')).toBeInTheDocument();
    });

    it('renders a quick-like message (lone 👍, no attachment) without the normal bubble chrome', () => {
        const { container } = render(
            <MessageBubble message={baseMessage({ content: '👍' })} own showSeen={false} onReact={vi.fn()} onRemoveReaction={vi.fn()} />,
        );
        expect(container.querySelector('.msg-bubble--emoji')).toBeInTheDocument();
    });

    it('does not treat a 👍 message with an attachment as a quick-like', () => {
        const { container } = render(
            <MessageBubble
                message={baseMessage({
                    content: '👍',
                    attachment: { content_type: 'image/png', filename: 'a.png', size: 10, url: '/x' },
                })}
                own={false}
                showSeen={false}
                onReact={vi.fn()}
                onRemoveReaction={vi.fn()}
            />,
        );
        expect(container.querySelector('.msg-bubble--emoji')).not.toBeInTheDocument();
    });

    it('renders an inline image for an image attachment', () => {
        render(
            <MessageBubble
                message={baseMessage({
                    content: '',
                    attachment: { content_type: 'image/png', filename: 'photo.png', size: 10, url: '/attachment-url' },
                })}
                own={false}
                showSeen={false}
                onReact={vi.fn()}
                onRemoveReaction={vi.fn()}
            />,
        );
        const img = screen.getByAltText('photo.png') as HTMLImageElement;
        expect(img.src).toContain('/attachment-url');
    });

    it('renders a file chip (not an <img>) for a non-image attachment', () => {
        render(
            <MessageBubble
                message={baseMessage({
                    content: '',
                    attachment: { content_type: 'application/pdf', filename: 'report.pdf', size: 10, url: '/attachment-url' },
                })}
                own={false}
                showSeen={false}
                onReact={vi.fn()}
                onRemoveReaction={vi.fn()}
            />,
        );
        expect(screen.getByText('report.pdf')).toBeInTheDocument();
        expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });

    it('renders aggregated reaction badges, highlighting the one that is mine', () => {
        const { container } = render(
            <MessageBubble
                message={baseMessage({
                    reactions: [
                        { emoji: '👍', count: 2, reacted_by_me: true },
                        { emoji: '❤️', count: 1, reacted_by_me: false },
                    ],
                })}
                own={false}
                showSeen={false}
                onReact={vi.fn()}
                onRemoveReaction={vi.fn()}
            />,
        );
        expect(screen.getByText('👍 2')).toBeInTheDocument();
        expect(screen.getByText('❤️ 1')).toBeInTheDocument();
        expect(container.querySelector('.reaction-badge.mine')).toHaveTextContent('👍 2');
    });

    it('clicking a reaction badge that is already mine removes it', () => {
        const onRemoveReaction = vi.fn();
        render(
            <MessageBubble
                message={baseMessage({ reactions: [{ emoji: '👍', count: 1, reacted_by_me: true }] })}
                own={false}
                showSeen={false}
                onReact={vi.fn()}
                onRemoveReaction={onRemoveReaction}
            />,
        );
        fireEvent.click(screen.getByText('👍 1'));
        expect(onRemoveReaction).toHaveBeenCalledTimes(1);
    });

    it('clicking a reaction badge that is not mine sets that reaction', () => {
        const onReact = vi.fn();
        render(
            <MessageBubble
                message={baseMessage({ reactions: [{ emoji: '❤️', count: 1, reacted_by_me: false }] })}
                own={false}
                showSeen={false}
                onReact={onReact}
                onRemoveReaction={vi.fn()}
            />,
        );
        fireEvent.click(screen.getByText('❤️ 1'));
        expect(onReact).toHaveBeenCalledWith('❤️');
    });

    it('opens the emoji picker on trigger click, offering all 6 quick reactions', () => {
        const { container } = render(
            <MessageBubble message={baseMessage()} own={false} showSeen={false} onReact={vi.fn()} onRemoveReaction={vi.fn()} />,
        );
        expect(container.querySelector('.reaction-picker')).not.toBeInTheDocument();

        fireEvent.click(screen.getByLabelText('Thả cảm xúc'));

        expect(container.querySelectorAll('.reaction-picker-emoji')).toHaveLength(6);
        ['👍', '❤️', '😂', '😮', '😢', '😡'].forEach((emoji) => {
            expect(screen.getByText(emoji, { selector: '.reaction-picker-emoji' })).toBeInTheDocument();
        });
    });

    it('picking a new emoji from the picker calls onReact and closes the picker', () => {
        const onReact = vi.fn();
        render(<MessageBubble message={baseMessage()} own={false} showSeen={false} onReact={onReact} onRemoveReaction={vi.fn()} />);
        fireEvent.click(screen.getByLabelText('Thả cảm xúc'));

        fireEvent.click(screen.getByText('😂', { selector: '.reaction-picker-emoji' }));

        expect(onReact).toHaveBeenCalledWith('😂');
        expect(screen.queryByText('😂', { selector: '.reaction-picker-emoji' })).not.toBeInTheDocument();
    });

    it('opens the emoji picker on hover, without needing a click', () => {
        const { container } = render(
            <MessageBubble message={baseMessage()} own={false} showSeen={false} onReact={vi.fn()} onRemoveReaction={vi.fn()} />,
        );
        expect(container.querySelector('.reaction-picker')).not.toBeInTheDocument();

        fireEvent.mouseEnter(container.querySelector('.msg-react-zone') as HTMLElement);
        expect(container.querySelector('.reaction-picker')).toBeInTheDocument();

        fireEvent.mouseLeave(container.querySelector('.msg-react-zone') as HTMLElement);
        expect(container.querySelector('.reaction-picker')).not.toBeInTheDocument();
    });

    it('picking the already-active emoji from the picker removes the reaction instead of re-adding it', () => {
        const onReact = vi.fn();
        const onRemoveReaction = vi.fn();
        render(
            <MessageBubble
                message={baseMessage({ reactions: [{ emoji: '👍', count: 1, reacted_by_me: true }] })}
                own={false}
                showSeen={false}
                onReact={onReact}
                onRemoveReaction={onRemoveReaction}
            />,
        );
        fireEvent.click(screen.getByLabelText('Thả cảm xúc'));

        fireEvent.click(screen.getByText('👍', { selector: '.reaction-picker-emoji' }));

        expect(onRemoveReaction).toHaveBeenCalledTimes(1);
        expect(onReact).not.toHaveBeenCalled();
    });
});
