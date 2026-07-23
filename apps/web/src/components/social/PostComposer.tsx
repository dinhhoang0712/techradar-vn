import { useState } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import { createPost } from '../../api/socialService';
import { useToast } from '../common/toastContext';
import Avatar from '../common/Avatar';
import MentionTextarea from './MentionTextarea';
import CompanyTagPicker from './CompanyTagPicker';
import { fileToBase64 } from '../../utils/fileToBase64';
import type { Post, PostAuthor } from '../../types/social';
import type { Company } from '../../types/company';

const MAX_IMAGES = 4;
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;

interface ComposerImage {
    id: string;
    file: File;
    dataUrl: string;
}

interface PostComposerProps {
    currentUser: PostAuthor | null;
    currentUserId: string | null;
    onPosted: (post: Post) => void;
}

// Ô soạn bài viết đầu Bảng tin — đăng xong gọi `onPosted` với bài viết dựng optimistic để FeedPage
// chèn lên đầu feed ngay, không cần chờ tải lại toàn bộ danh sách.
export default function PostComposer({ currentUser, currentUserId, onPosted }: PostComposerProps) {
    const [content, setContent] = useState('');
    const [mentionedIds, setMentionedIds] = useState<string[]>([]);
    const [composerImages, setComposerImages] = useState<ComposerImage[]>([]);
    const [taggedCompany, setTaggedCompany] = useState<Company | null>(null);
    const [posting, setPosting] = useState(false);
    const [composerFocused, setComposerFocused] = useState(false);
    const notify = useToast();

    const handleImageSelect = async (e: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(e.target.files || []);
        e.target.value = ''; // allow re-selecting the same file
        if (files.length === 0) return;
        if (composerImages.length + files.length > MAX_IMAGES) {
            notify({ title: `Tối đa ${MAX_IMAGES} ảnh mỗi bài viết`, variant: 'error' });
            return;
        }
        const oversized = files.find((f) => f.size > MAX_IMAGE_BYTES);
        if (oversized) {
            notify({ title: 'Ảnh quá lớn (tối đa 3MB mỗi ảnh)', variant: 'error' });
            return;
        }
        try {
            const withData = await Promise.all(files.map(async (file) => ({
                id: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
                file,
                dataUrl: await fileToBase64(file),
            })));
            setComposerImages((prev) => [...prev, ...withData]);
        } catch {
            notify({ title: 'Không thể đọc ảnh', variant: 'error' });
        }
    };

    const removeComposerImage = (id: string) => {
        setComposerImages((prev) => prev.filter((img) => img.id !== id));
    };

    const handlePost = async (e: FormEvent) => {
        e.preventDefault();
        const trimmed = content.trim();
        if (!trimmed) return;
        setPosting(true);
        try {
            const images = composerImages.map((img) => ({ contentType: img.file.type || 'image/png', dataBase64: img.dataUrl }));
            const res = await createPost(trimmed, {
                images,
                taggedCompanyId: taggedCompany?.id,
                mentionedUserIds: mentionedIds,
            });
            onPosted({
                id: res?.data?.id || `tmp-${Date.now()}`,
                author: { id: currentUserId ?? '', full_name: 'Bạn', avatar_url: null },
                content: trimmed,
                created_at: new Date().toISOString(),
                like_count: 0,
                comment_count: 0,
                liked_by_me: false,
                image_urls: composerImages.map((img) => img.dataUrl),
                hashtags: [],
                tagged_company: taggedCompany
                    ? { id: taggedCompany.id, name: taggedCompany.name, location: taggedCompany.location }
                    : null,
            });
            setContent('');
            setMentionedIds([]);
            setComposerImages([]);
            setTaggedCompany(null);
        } catch (err) {
            notify({ title: 'Không thể đăng bài', body: (err as Error).message, variant: 'error' });
        } finally {
            setPosting(false);
        }
    };

    return (
        <div className={`card feed-composer${composerFocused ? ' is-focused' : ''}`}>
            <form onSubmit={handlePost}>
                <div className="feed-composer-row">
                    <Avatar user={currentUser} size={40} />
                    <MentionTextarea
                        as="textarea"
                        className="feed-composer-input"
                        placeholder="Bạn đang nghĩ gì về công nghệ hôm nay? Dùng #hashtag hoặc @tên để nhắc ai đó"
                        value={content}
                        onChange={setContent}
                        mentionedUserIds={mentionedIds}
                        onMentionedUserIdsChange={setMentionedIds}
                        onFocus={() => setComposerFocused(true)}
                        onBlur={() => setComposerFocused(false)}
                        maxLength={2000}
                        rows={3}
                        disabled={posting}
                    />
                </div>

                {composerImages.length > 0 && (
                    <div className="feed-composer-image-strip">
                        {composerImages.map((img) => (
                            <div key={img.id} className="feed-composer-thumb">
                                <img src={img.dataUrl} alt="" />
                                <button
                                    type="button"
                                    className="feed-composer-thumb-remove"
                                    onClick={() => removeComposerImage(img.id)}
                                    aria-label="Bỏ ảnh"
                                >
                                    ✕
                                </button>
                            </div>
                        ))}
                    </div>
                )}

                <div className="feed-composer-tools">
                    <label className={`btn btn-ghost feed-composer-tool-btn${composerImages.length >= MAX_IMAGES ? ' is-disabled' : ''}`}>
                        🖼️ Ảnh
                        <input
                            type="file"
                            accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                            multiple
                            hidden
                            onChange={handleImageSelect}
                            disabled={posting || composerImages.length >= MAX_IMAGES}
                        />
                    </label>
                    <CompanyTagPicker
                        selected={taggedCompany}
                        onSelect={setTaggedCompany}
                        onClear={() => setTaggedCompany(null)}
                    />
                </div>

                <div className="feed-composer-footer">
                    <span className="feed-composer-count">{content.length}/2000</span>
                    <button type="submit" className="btn btn-primary" disabled={posting || !content.trim()}>
                        {posting ? 'Đang đăng...' : 'Đăng bài'}
                    </button>
                </div>
            </form>
        </div>
    );
}
