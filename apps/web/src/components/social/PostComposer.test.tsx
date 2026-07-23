import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider } from '../common/ToastProvider';
import PostComposer from './PostComposer';
import { createPost } from '../../api/socialService';
import type { ComponentProps } from 'react';

vi.mock('../../api/socialService', () => ({
    createPost: vi.fn(),
}));

const mockedCreatePost = vi.mocked(createPost);

const PLACEHOLDER = /Bạn đang nghĩ gì về công nghệ hôm nay/;

function renderComposer(props: Partial<ComponentProps<typeof PostComposer>> = {}) {
    const onPosted = vi.fn();
    render(
        <ToastProvider>
            <PostComposer currentUser={{ id: 'me' }} currentUserId="me" onPosted={onPosted} {...props} />
        </ToastProvider>,
    );
    return { onPosted };
}

describe('PostComposer', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('disables submit until there is non-whitespace content', async () => {
        const user = userEvent.setup();
        renderComposer();
        const submit = screen.getByRole('button', { name: 'Đăng bài' });
        expect(submit).toBeDisabled();

        await user.type(screen.getByPlaceholderText(PLACEHOLDER), '   ');
        expect(submit).toBeDisabled();

        await user.type(screen.getByPlaceholderText(PLACEHOLDER), 'Hello');
        expect(submit).toBeEnabled();
    });

    it('posts trimmed content and reports the new post via onPosted, then clears the composer', async () => {
        mockedCreatePost.mockResolvedValueOnce({ data: { id: 'post-1' } } as never);
        const user = userEvent.setup();
        const { onPosted } = renderComposer();

        await user.type(screen.getByPlaceholderText(PLACEHOLDER), '  Hello world  ');
        await user.click(screen.getByRole('button', { name: 'Đăng bài' }));

        await waitFor(() => expect(onPosted).toHaveBeenCalledTimes(1));
        expect(mockedCreatePost).toHaveBeenCalledWith('Hello world', {
            images: [],
            taggedCompanyId: undefined,
            mentionedUserIds: [],
        });
        expect(onPosted).toHaveBeenCalledWith(expect.objectContaining({
            id: 'post-1',
            content: 'Hello world',
            author: expect.objectContaining({ id: 'me' }),
        }));

        expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue('');
    });

    it('shows an error toast and keeps the draft when createPost fails', async () => {
        mockedCreatePost.mockRejectedValueOnce(new Error('Server unreachable'));
        const user = userEvent.setup();
        const { onPosted } = renderComposer();

        await user.type(screen.getByPlaceholderText(PLACEHOLDER), 'Will fail');
        await user.click(screen.getByRole('button', { name: 'Đăng bài' }));

        expect(await screen.findByText('Không thể đăng bài')).toBeInTheDocument();
        expect(onPosted).not.toHaveBeenCalled();
        expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue('Will fail');
    });

    it('rejects selecting more than 4 images at once', async () => {
        const user = userEvent.setup();
        renderComposer();

        const files = Array.from({ length: 5 }, (_, i) => new File(['x'], `a${i}.png`, { type: 'image/png' }));
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        await user.upload(input, files);

        expect(await screen.findByText('Tối đa 4 ảnh mỗi bài viết')).toBeInTheDocument();
    });
});
