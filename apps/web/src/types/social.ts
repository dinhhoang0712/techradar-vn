// Domain types cho Bảng tin (feed/post/comment) — dùng chung giữa socialService, useFeed,
// PostCard, PostComposer.
export interface PostAuthor {
    id: string;
    full_name?: string;
    avatar_url?: string | null;
}

export interface TaggedCompany {
    id: string;
    name: string;
    location?: string;
}

export interface Post {
    id: string;
    author?: PostAuthor;
    content: string;
    created_at: string;
    like_count: number;
    comment_count: number;
    liked_by_me?: boolean;
    image_urls?: string[];
    hashtags?: string[];
    tagged_company?: TaggedCompany | null;
    [key: string]: unknown;
}

export interface FeedLiveEvent {
    type: 'POST_CREATED' | 'POST_LIKED' | 'COMMENT_ADDED';
    post?: Post;
    post_id?: string;
    like_count?: number;
    comment_count?: number;
}

export interface Comment {
    id: string | number;
    parent_id?: string | number | null;
    content: string;
    author?: PostAuthor;
    created_at?: string;
    [key: string]: unknown;
}
