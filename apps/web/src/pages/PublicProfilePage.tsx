import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getProfileSummary, getUserPosts, followUser, unfollowUser } from '../api/socialService';
import type { ProfileSummary } from '../api/socialService';
import type { Post } from '../types/social';
import { useToast } from '../components/common/toastContext';
import { useMessagingContext } from '../contexts/messagingStore';
import Avatar from '../components/common/Avatar';
import PostCard from '../components/social/PostCard';
import './PublicProfilePage.css';

export default function PublicProfilePage() {
    const { id } = useParams<'id'>();
    const navigate = useNavigate();
    const [profile, setProfile] = useState<ProfileSummary | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [followBusy, setFollowBusy] = useState(false);
    const [messageBusy, setMessageBusy] = useState(false);
    const [posts, setPosts] = useState<Post[]>([]);
    const [postsLoading, setPostsLoading] = useState(true);
    const notify = useToast();
    const { currentUserId, openConversationWith } = useMessagingContext()!;

    const loadProfile = useCallback(async () => {
        setLoading(true);
        setError(false);
        try {
            const res = await getProfileSummary(id!);
            setProfile(res?.data ?? null);
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        loadProfile();
    }, [loadProfile]);

    useEffect(() => {
        setPostsLoading(true);
        getUserPosts(id!)
            .then((res) => setPosts(res?.data ?? []))
            .catch(() => setPosts([]))
            .finally(() => setPostsLoading(false));
    }, [id]);

    const toggleFollow = async () => {
        if (!profile) return;
        setFollowBusy(true);
        const wasFollowing = profile.is_following;
        setProfile((p) => p && ({ ...p, is_following: !wasFollowing, follower_count: Math.max(0, (p.follower_count ?? 0) + (wasFollowing ? -1 : 1)) }));
        try {
            await (wasFollowing ? unfollowUser(id!) : followUser(id!));
        } catch (err) {
            setProfile((p) => p && ({ ...p, is_following: wasFollowing, follower_count: Math.max(0, (p.follower_count ?? 0) + (wasFollowing ? 1 : -1)) }));
            notify({ title: 'Không thể cập nhật theo dõi', body: (err as Error).message, variant: 'error' });
        } finally {
            setFollowBusy(false);
        }
    };

    const handleDeleted = (postId: string) => {
        setPosts((prev) => prev.filter((p) => p.id !== postId));
    };

    const handleMessage = async () => {
        setMessageBusy(true);
        try {
            const conversationId = await openConversationWith(id!);
            if (conversationId) navigate(`/messages?conversation=${conversationId}`);
        } catch (err) {
            notify({ title: 'Không thể mở cuộc trò chuyện', body: (err as Error).message, variant: 'error' });
        } finally {
            setMessageBusy(false);
        }
    };

    if (loading) {
        return <div className="public-profile-state">Đang tải hồ sơ...</div>;
    }

    if (error || !profile) {
        return (
            <div className="public-profile-state">
                <span>Không tìm thấy người dùng này.</span>
                <button className="btn btn-ghost mt-16" onClick={loadProfile}>Thử lại</button>
            </div>
        );
    }

    const isOwnProfile = currentUserId && currentUserId === profile.id;

    return (
        <div className="public-profile-page">
            <div className="card public-profile-header">
                <Avatar user={profile} size={72} ring />

                <div className="public-profile-info">
                    <div className="public-profile-name-row">
                        <h1 className="public-profile-name">{profile.full_name}</h1>
                        {!isOwnProfile && (
                            <div className="public-profile-actions">
                                <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={handleMessage}
                                    disabled={messageBusy}
                                >
                                    Nhắn tin
                                </button>
                                <button
                                    type="button"
                                    className={`btn ${profile.is_following ? 'btn-ghost' : 'btn-primary'}`}
                                    onClick={toggleFollow}
                                    disabled={followBusy}
                                >
                                    {profile.is_following ? 'Bỏ theo dõi' : 'Theo dõi'}
                                </button>
                            </div>
                        )}
                    </div>

                    {(profile.job_role || profile.location) && (
                        <p className="public-profile-meta">
                            {profile.job_role}
                            {profile.job_role && profile.location ? ' · ' : ''}
                            {profile.location}
                        </p>
                    )}

                    {profile.bio && <p className="public-profile-bio">{profile.bio}</p>}

                    <div className="public-profile-stats">
                        <div className="public-profile-stat-chip">
                            <span className="public-profile-stat-value">{profile.post_count}</span>
                            <span className="public-profile-stat-label">bài viết</span>
                        </div>
                        <div className="public-profile-stat-chip">
                            <span className="public-profile-stat-value">{profile.follower_count}</span>
                            <span className="public-profile-stat-label">người theo dõi</span>
                        </div>
                        <div className="public-profile-stat-chip">
                            <span className="public-profile-stat-value">{profile.following_count}</span>
                            <span className="public-profile-stat-label">đang theo dõi</span>
                        </div>
                    </div>
                </div>
            </div>

            <div className="feed-list mt-16">
                {postsLoading ? (
                    <div className="public-profile-state">Đang tải bài viết...</div>
                ) : posts.length === 0 ? (
                    <div className="public-profile-state">Chưa có bài viết nào.</div>
                ) : (
                    posts.map((post) => (
                        <PostCard key={post.id} post={post} currentUserId={currentUserId} onDeleted={handleDeleted} />
                    ))
                )}
            </div>
        </div>
    );
}
