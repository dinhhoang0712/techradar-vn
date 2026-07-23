import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useFeed } from './useFeed';
import { getFeed, streamFeed } from '../api/socialService';
import type { FeedLiveEvent } from '../types/social';

vi.mock('../api/socialService', () => ({
    getFeed: vi.fn(),
    streamFeed: vi.fn(),
}));

const mockedGetFeed = vi.mocked(getFeed);
const mockedStreamFeed = vi.mocked(streamFeed);

describe('useFeed', () => {
    let liveHandler: (event: FeedLiveEvent) => void;
    let abortFn: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.clearAllMocks();
        abortFn = vi.fn();
        mockedStreamFeed.mockImplementation((_scope, onEvent) => {
            liveHandler = onEvent;
            return { abort: abortFn } as unknown as AbortController;
        });
    });

    it('loads the first page on mount', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: [{ id: '1' }, { id: '2' }] } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));

        expect(result.current.loading).toBe(true);
        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.posts).toEqual([{ id: '1' }, { id: '2' }]);
        expect(mockedGetFeed).toHaveBeenCalledWith(0, 20, { scope: 'following', hashtag: null });
    });

    it('sets error=true when the initial load fails', async () => {
        mockedGetFeed.mockRejectedValueOnce(new Error('boom'));
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.error).toBe(true);
    });

    it('appends results and clears hasMore once a short page returns', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: Array.from({ length: 20 }, (_, i) => ({ id: `p${i}` })) } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.hasMore).toBe(true);

        mockedGetFeed.mockResolvedValueOnce({ data: [{ id: 'p20' }] } as never);
        await act(async () => {
            await result.current.loadMore();
        });
        expect(result.current.posts).toHaveLength(21);
        expect(result.current.hasMore).toBe(false);
        expect(mockedGetFeed).toHaveBeenLastCalledWith(1, 20, { scope: 'following', hashtag: null });
    });

    it('returns false from loadMore on failure', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: [] } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));

        mockedGetFeed.mockRejectedValueOnce(new Error('boom'));
        let ok;
        await act(async () => {
            ok = await result.current.loadMore();
        });
        expect(ok).toBe(false);
    });

    it('addPost prepends and removePost filters out a post', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: [] } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() => result.current.addPost({ id: 'new' } as never));
        expect(result.current.posts).toEqual([{ id: 'new' }]);

        act(() => result.current.removePost('new'));
        expect(result.current.posts).toEqual([]);
    });

    it('prepends a live POST_CREATED event from someone else, but ignores its own post (already optimistic)', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: [] } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() => liveHandler({ type: 'POST_CREATED', post: { id: 'p1', author: { id: 'other' } } } as never));
        expect(result.current.posts).toEqual([{ id: 'p1', author: { id: 'other' } }]);

        act(() => liveHandler({ type: 'POST_CREATED', post: { id: 'p2', author: { id: 'me' } } } as never));
        expect(result.current.posts).toHaveLength(1); // own post ignored — already added optimistically
    });

    it('applies live like/comment count updates to the matching post', async () => {
        mockedGetFeed.mockResolvedValueOnce({ data: [{ id: 'p1', like_count: 0, comment_count: 0 }] } as never);
        const { result } = renderHook(() => useFeed('following', null, 'me'));
        await waitFor(() => expect(result.current.loading).toBe(false));

        act(() => liveHandler({ type: 'POST_LIKED', post_id: 'p1', like_count: 5 }));
        expect(result.current.posts[0].like_count).toBe(5);

        act(() => liveHandler({ type: 'COMMENT_ADDED', post_id: 'p1', comment_count: 2 }));
        expect(result.current.posts[0].comment_count).toBe(2);
    });

    it('reconnects the live stream when scope changes', async () => {
        mockedGetFeed.mockResolvedValue({ data: [] } as never);
        const { rerender } = renderHook(({ scope }) => useFeed(scope, null, 'me'), { initialProps: { scope: 'following' } });
        await waitFor(() => expect(mockedStreamFeed).toHaveBeenCalledTimes(1));

        rerender({ scope: 'explore' });
        await waitFor(() => expect(abortFn).toHaveBeenCalledTimes(1));
        expect(mockedStreamFeed).toHaveBeenCalledTimes(2);
    });
});
