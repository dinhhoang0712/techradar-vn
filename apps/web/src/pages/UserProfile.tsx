import { useState, useEffect } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { getUserProfile, updateUserProfile, uploadAvatar } from '../api/userService';
import { getRecommendations } from '../api/recommendService';
import { useToast } from '../components/common/toastContext';
import { fileToBase64 } from '../utils/fileToBase64';
import TechRecommendationCards from '../components/TechRecommendationCards';
import ProfileEditForm from '../components/profile/ProfileEditForm';
import type { ProfileFormState } from '../components/profile/ProfileEditForm';
import ProfileViewDetails from '../components/profile/ProfileViewDetails';
import type { ProfileViewData } from '../components/profile/ProfileViewDetails';
import type { UserProfileData, UpdateProfilePayload } from '../types/userProfile';
import type { NextSkill } from '../types/career';
import './UserProfile.css';

const EMPTY_PROFILE: ProfileViewData & { email?: string; avatar_url?: string } = {
    full_name: '', email: '', avatar_url: '', bio: '', job_role: '', location: '',
    technologies: [], notify_inapp: true, notify_email: true,
};

const EMPTY_FORM: ProfileFormState = {
    full_name: '', email: '', avatar_url: '', bio: '', job_role: '', location: '',
    password: '', technologies: '', notify_inapp: true, notify_email: true,
};

/**
 * UserProfile Page
 * Route: /profile
 * - Tải thông tin người dùng qua GET /user/profile
 * - Cho phép chỉnh sửa và lưu qua PUT /user/profile
 */
export default function UserProfile() {
    const [profile, setProfile] = useState<ProfileViewData & { email?: string; avatar_url?: string }>(EMPTY_PROFILE);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [editMode, setEditMode] = useState(false);
    const [form, setForm] = useState<ProfileFormState>(EMPTY_FORM);
    const [recommendations, setRecommendations] = useState<NextSkill[]>([]);
    const [recsBasedOn, setRecsBasedOn] = useState<string[]>([]);
    const [loadingRecs, setLoadingRecs] = useState(false);
    const notify = useToast();

    useEffect(() => {
        loadProfile();
        // Mount-only: loadProfile is recreated every render, adding it here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadRecommendations = async (techs: string[]) => {
        setLoadingRecs(true);
        try {
            const res = await getRecommendations(techs, 8);
            const data = ('data' in res ? res.data : res) ?? { recommendations: [], based_on: [] };
            setRecommendations((data.recommendations ?? []) as NextSkill[]);
            setRecsBasedOn(data.based_on ?? []);
        } catch {
            setRecommendations([]);
            setRecsBasedOn([]);
        } finally {
            setLoadingRecs(false);
        }
    };

    const loadProfile = async () => {
        setLoading(true);
        try {
            const res = await getUserProfile();
            const data: UserProfileData = ('data' in res ? res.data : res) ?? {};

            // Hàm API trả về dạng { user: {...}, profile: {...} }
            const flatData = {
                full_name: data.user?.full_name || data.full_name || '',
                email: data.user?.email || data.email || '',
                job_role: data.profile?.job_role || data.job_role || '',
                bio: data.profile?.bio || data.bio || '',
                location: data.profile?.location || data.location || '',
                technologies: data.profile?.technologies || data.technologies || [],
                avatar_url: data.profile?.avatar_url || data.avatar_url || '',
                notify_inapp: (data.profile?.notify_inapp ?? data.notify_inapp) !== false,
                notify_email: (data.profile?.notify_email ?? data.notify_email) !== false
            };

            setProfile(flatData);
            setForm({
                ...flatData,
                password: '',
                technologies: flatData.technologies && flatData.technologies.length > 0 ? flatData.technologies.join(', ') : ''
            });
            // Tải recommendations dựa trên tech trong profile
            if (flatData.technologies?.length > 0) {
                loadRecommendations(flatData.technologies);
            }
        } catch (err) {
            showToast('error', (err as Error).message || 'Không thể tải thông tin người dùng. Vui lòng thử lại.');
            console.error('[UserProfile] Load error:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            const payload: UpdateProfilePayload = {
                full_name: form.full_name,
                bio: form.bio,
                job_role: form.job_role,
                location: form.location,
                technologies: form.technologies ? form.technologies.split(',').map(t => t.trim()).filter(Boolean) : [],
                notify_inapp: form.notify_inapp,
                notify_email: form.notify_email
            };
            if (form.password) {
                payload.password = form.password;
            }

            await updateUserProfile(payload);

            // Tải lại dữ liệu từ server để đảm bảo tính nhất quán
            await loadProfile();

            setEditMode(false);
            showToast('success', 'Cập nhật thông tin thành công!');
        } catch (err) {
            showToast('error', (err as Error).message || 'Cập nhật thất bại. Vui lòng thử lại.');
            console.error('[UserProfile] Save error:', err);
        } finally {
            setSaving(false);
        }
    };

    const handleCancel = () => {
        setForm({
            ...EMPTY_FORM,
            ...profile,
            password: '',
            technologies: profile.technologies ? profile.technologies.join(', ') : ''
        });
        setEditMode(false);
    };

    const showToast = (type: 'error' | 'success', message: string) => {
        notify({ title: message, variant: type === 'error' ? 'error' : 'success' });
    };

    const handleAvatarChange = async (e: ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        e.target.value = ''; // allow re-selecting the same file
        if (!file) return;
        if (file.size > 3 * 1024 * 1024) {
            showToast('error', 'Ảnh quá lớn (tối đa 3MB).');
            return;
        }
        try {
            const dataUrl = await fileToBase64(file);
            const res = await uploadAvatar(file.type || 'image/png', dataUrl);
            const url = res?.data?.avatar_url;
            if (url) {
                const busted = `${url}?t=${Date.now()}`;
                setProfile(p => ({ ...p, avatar_url: busted }));
                setForm(f => ({ ...f, avatar_url: busted }));
            }
        } catch (err) {
            showToast('error', (err as Error).message || 'Tải ảnh thất bại');
        }
    };

    return (
        <div className="user-profile-page">
            <div className="profile-container">
                {/* Header */}
                <div className="profile-header">
                    <h1 className="profile-title">Thông tin cá nhân</h1>
                    <p className="profile-subtitle">Xem và quản lý thông tin tài khoản của bạn</p>
                </div>

                {loading ? (
                    <div className="profile-skeleton">
                        <div className="skeleton-avatar" />
                        <div className="skeleton-lines">
                            <div className="skeleton-line skeleton-line--wide" />
                            <div className="skeleton-line skeleton-line--medium" />
                            <div className="skeleton-line skeleton-line--narrow" />
                        </div>
                    </div>
                ) : (
                    <div className="profile-card">
                        {/* Avatar section */}
                        <div className="profile-avatar-section">
                            <div className="profile-avatar-ring gradient-ring active">
                                <div className="profile-avatar">
                                    {profile.avatar_url ? (
                                        <img src={profile.avatar_url} alt="Avatar" className="profile-avatar-img" />
                                    ) : (
                                        <div className="profile-avatar-icon">
                                            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                                                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                                                <circle cx="12" cy="7" r="4"></circle>
                                            </svg>
                                        </div>
                                    )}
                                    <label className="profile-avatar-edit" title="Đổi ảnh đại diện">
                                        📷
                                        <input
                                            type="file"
                                            accept="image/*"
                                            style={{ display: 'none' }}
                                            onChange={handleAvatarChange}
                                        />
                                    </label>
                                </div>
                            </div>
                            <div className="profile-identity">
                                <h2 className="profile-name">{profile.full_name || 'Người dùng'}</h2>
                                <p className="profile-email">{profile.email || '—'}</p>
                            </div>
                        </div>

                        {/* Details section */}
                        {editMode ? (
                            <ProfileEditForm
                                form={form}
                                setForm={setForm}
                                saving={saving}
                                onSubmit={handleSave}
                                onCancel={handleCancel}
                            />
                        ) : (
                            <ProfileViewDetails profile={profile} onEdit={() => setEditMode(true)} />
                        )}
                    </div>
                )}
            </div>

            {/* Recommendations section */}
            <TechRecommendationCards
                recommendations={recommendations}
                basedOn={recsBasedOn}
                loading={loadingRecs}
            />
        </div>
    );
}
