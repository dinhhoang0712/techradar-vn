import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ChatMessageBubble from './ChatMessageBubble';
import type { ChatUiMessage } from '../../types/chat';

describe('ChatMessageBubble', () => {
    it('shows the thinking dots (not an empty bubble) while streaming with no text yet', () => {
        const msg: ChatUiMessage = { id: 1, role: 'bot', text: '', streaming: true };
        const { container } = render(<ChatMessageBubble msg={msg} />);

        expect(container.querySelector('.dots-animation')).toBeInTheDocument();
        expect(container.querySelector('.cursor-blink')).not.toBeInTheDocument();
    });

    it('shows the streamed text with a blinking cursor once tokens start arriving', () => {
        const msg: ChatUiMessage = { id: 2, role: 'bot', text: 'Xin chào', streaming: true };
        const { container } = render(<ChatMessageBubble msg={msg} />);

        expect(screen.getByText('Xin chào')).toBeInTheDocument();
        expect(container.querySelector('.cursor-blink')).toBeInTheDocument();
        expect(container.querySelector('.dots-animation')).not.toBeInTheDocument();
    });

    it('shows the finished text with no cursor once streaming ends', () => {
        const msg: ChatUiMessage = { id: 3, role: 'bot', text: 'Xong rồi', streaming: false };
        const { container } = render(<ChatMessageBubble msg={msg} />);

        expect(screen.getByText('Xong rồi')).toBeInTheDocument();
        expect(container.querySelector('.cursor-blink')).not.toBeInTheDocument();
        expect(container.querySelector('.dots-animation')).not.toBeInTheDocument();
    });

    it('shows the thinking dots for the entire duration of an Agent-mode reply (no incremental text)', () => {
        // Agent mode never streams partial tokens — text stays '' until the whole answer lands —
        // so the same "streaming && no text yet" condition covers it with no separate flag needed.
        const msg: ChatUiMessage = { id: 4, role: 'bot', text: '', streaming: true };
        const { container } = render(<ChatMessageBubble msg={msg} />);
        expect(container.querySelector('.dots-animation')).toBeInTheDocument();
    });

    it('renders a user avatar (not a bot avatar) for user messages', () => {
        const msg: ChatUiMessage = { id: 5, role: 'user', text: 'Hỏi gì đó' };
        const { container } = render(<ChatMessageBubble msg={msg} />);

        expect(container.querySelector('.user-avatar')).toBeInTheDocument();
        expect(container.querySelector('.bot-avatar')).not.toBeInTheDocument();
    });

    it('renders agent steps when present', () => {
        const msg: ChatUiMessage = {
            id: 6, role: 'bot', text: 'Đã xong', streaming: false,
            steps: [{ tool: 'search', input: 'React trends' }],
        };
        render(<ChatMessageBubble msg={msg} />);
        expect(screen.getByText('Các bước đã thực hiện (1)')).toBeInTheDocument();
    });
});
