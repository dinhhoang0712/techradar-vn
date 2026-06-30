import { useState, useEffect } from 'react';
import { getUserProfile, updateUserProfile, uploadAvatar } from '../api/userService';
import { getRecommendations } from '../api/recommendService';
import './UserProfile.css';

/**
 * UserProfile Page
 * Route: /profile
 * - Tải thông tin người dùng qua GET /user/profile
 * - Cho phép chỉnh sửa và lưu qua PUT /user/profile
 */
export default function UserProfile() {
    const [profile, setProfile] = useState({ full_name: '', email: '', avatar_url: '', bio: '', job_role: '', location: '', technologies: [], notify_inapp: true, notify_email: true });
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [editMode, setEditMode] = useState(false);
    const [form, setForm] = useState({ full_name: '', email: '', avatar_url: '', bio: '', job_role: '', location: '', password: '', technologies: '', notify_inapp: true, notify_email: true });
    const [toast, setToast] = useState(null); // { type: 'success' | 'error', message: string }
    const [recommendations, setRecommendations] = useState([]);
    const [loadingRecs, setLoadingRecs] = useState(false);

    useEffect(() => {
        loadProfile();
    }, []);

    const loadRecommendations = async (techs) => {
        setLoadingRecs(true);
        try {
            const res = await getRecommendations(techs, 8);
            setRecommendations(res?.data?.recommendations ?? res?.recommendations ?? []);
        } catch {
            setRecommendations([]);
        } finally {
            setLoadingRecs(false);
        }
    };

    const loadProfile = async () => {
        setLoading(true);
        try {
            const res = await getUserProfile();
            const data = res?.data ?? res ?? {};
            
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
            showToast('error', 'Không thể tải thông tin người dùng. Vui lòng thử lại.');
            console.error('[UserProfile] Load error:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            const payload = {
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
            showToast('error', 'Cập nhật thất bại. Vui lòng thử lại.');
            console.error('[UserProfile] Save error:', err);
        } finally {
            setSaving(false);
        }
    };

    const handleCancel = () => {
        setForm({
            ...profile,
            password: '',
            technologies: profile.technologies ? profile.technologies.join(', ') : ''
        });
        setEditMode(false);
    };

    const showToast = (type, message) => {
        setToast({ type, message });
        setTimeout(() => setToast(null), 3500);
    };

    const handleAvatarChange = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = ''; // allow re-selecting the same file
        if (!file) return;
        if (file.size > 3 * 1024 * 1024) {
            alert('Ảnh quá lớn (tối đa 3MB).');
            return;
        }
        try {
            const dataUrl = await new Promise((resolve, reject) => {
                const reader = new FileReader();
                reader.onload = () => resolve(reader.result);
                reader.onerror = reject;
                reader.readAsDataURL(file);
            });
            const res = await uploadAvatar(file.type || 'image/png', dataUrl);
            const url = res?.data?.avatar_url || res?.avatar_url;
            if (url) {
                const busted = `${url}?t=${Date.now()}`;
                setProfile(p => ({ ...p, avatar_url: busted }));
                setForm(f => ({ ...f, avatar_url: busted }));
            }
        } catch (err) {
            alert(err.message || 'Tải ảnh thất bại');
        }
    };

    const avatarLetter = profile.full_name
        ? profile.full_name.charAt(0).toUpperCase()
        : profile.email
        ? profile.email.charAt(0).toUpperCase()
        : 'U';

    return (
        <div className="user-profile-page">
            {/* Toast notification */}
            {toast && (
                <div className={`profile-toast profile-toast--${toast.type}`}>
                    {toast.message}
                </div>
            )}

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
                            <div className="profile-identity">
                                <h2 className="profile-name">{profile.full_name || 'Người dùng'}</h2>
                                <p className="profile-email">{profile.email || '—'}</p>
                            </div>
                        </div>

                        {/* Details section */}
                        {editMode ? (
                            <form className="profile-form" onSubmit={handleSave}>
                                <div className="form-group">
                                    <label htmlFor="full_name" className="form-label">Họ và tên</label>
                                    <input
                                        id="full_name"
                                        type="text"
                                        className="form-input"
                                        value={form.full_name || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, full_name: e.target.value }))}
                                        placeholder="Nhập họ và tên"
                                    />
                                </div>

                                <div className="form-group">
                                    <label htmlFor="job_role" className="form-label">Vai trò / Chức vụ</label>
                                    <input
                                        id="job_role"
                                        type="text"
                                        className="form-input"
                                        value={form.job_role || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, job_role: e.target.value }))}
                                        placeholder="VD: Software Engineer"
                                    />
                                </div>

                                <div className="form-group">
                                    <label htmlFor="bio" className="form-label">Giới thiệu (Bio)</label>
                                    <textarea
                                        id="bio"
                                        className="form-input"
                                        rows={3}
                                        value={form.bio || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, bio: e.target.value }))}
                                        placeholder="Vài nét về bản thân..."
                                    />
                                </div>

                                <div className="form-group">
                                    <label htmlFor="location" className="form-label">Địa điểm</label>
                                    <input
                                        id="location"
                                        type="text"
                                        className="form-input"
                                        value={form.location || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, location: e.target.value }))}
                                        placeholder="VD: Hà Nội, Việt Nam"
                                    />
                                </div>

                                <div className="form-group">
                                    <label htmlFor="technologies" className="form-label">Công nghệ</label>
                                    <input
                                        id="technologies"
                                        type="text"
                                        className="form-input"
                                        value={form.technologies || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, technologies: e.target.value }))}
                                        placeholder="VD: React, Node.js, Python"
                                    />
                                </div>

                                <div className="form-group">
                                    <label htmlFor="password" className="form-label">Mật khẩu mới (Để trống nếu không đổi)</label>
                                    <input
                                        id="password"
                                        type="password"
                                        className="form-input"
                                        value={form.password || ''}
                                        onChange={(e) => setForm(prev => ({ ...prev, password: e.target.value }))}
                                        placeholder="Nhập mật khẩu mới"
                                    />
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Thông báo</label>
                                    <label className="notif-pref">
                                        <input
                                            type="checkbox"
                                            checked={form.notify_inapp}
                                            onChange={(e) => setForm(prev => ({ ...prev, notify_inapp: e.target.checked }))}
                                        />
                                        <span>Nhận thông báo trong ứng dụng (chuông)</span>
                                    </label>
                                    <label className="notif-pref">
                                        <input
                                            type="checkbox"
                                            checked={form.notify_email}
                                            onChange={(e) => setForm(prev => ({ ...prev, notify_email: e.target.checked }))}
                                        />
                                        <span>Nhận thông báo qua email</span>
                                    </label>
                                </div>



                                {/* Action buttons */}
                                <div className="profile-actions">
                                    <button
                                        type="submit"
                                        className="btn btn-primary"
                                        disabled={saving}
                                        id="save-profile-btn"
                                    >
                                        {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                                    </button>
                                    <button
                                        type="button"
                                        className="btn btn-ghost"
                                        onClick={handleCancel}
                                        disabled={saving}
                                        id="cancel-edit-btn"
                                    >
                                        Huỷ
                                    </button>
                                </div>
                            </form>
                        ) : (
                            <div className="profile-form">
                                <div className="form-group">
                                    <label className="form-label">Họ và tên</label>
                                    <p className="form-value">{profile.full_name || <span className="form-empty">Chưa cập nhật</span>}</p>
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Vai trò / Chức vụ</label>
                                    <p className="form-value">{profile.job_role || <span className="form-empty">Chưa cập nhật</span>}</p>
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Giới thiệu (Bio)</label>
                                    <p className="form-value">{profile.bio || <span className="form-empty">Chưa cập nhật</span>}</p>
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Địa điểm</label>
                                    <p className="form-value">{profile.location || <span className="form-empty">Chưa cập nhật</span>}</p>
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Công nghệ</label>
                                    <p className="form-value">
                                        {profile.technologies && profile.technologies.length > 0
                                            ? profile.technologies.join(', ')
                                            : <span className="form-empty">Chưa cập nhật</span>}
                                    </p>
                                </div>

                                <div className="form-group">
                                    <label className="form-label">Thông báo</label>
                                    <p className="form-value">
                                        Trong ứng dụng: <strong>{profile.notify_inapp ? 'Bật' : 'Tắt'}</strong>
                                        {' · '}Email: <strong>{profile.notify_email ? 'Bật' : 'Tắt'}</strong>
                                    </p>
                                </div>



                                <div className="profile-actions">
                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        onClick={(e) => { e.preventDefault(); setEditMode(true); }}
                                        id="edit-profile-btn"
                                    >
                                        Chỉnh sửa thông tin
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Recommendations section */}
            {(loadingRecs || recommendations.length > 0) && (
                <div className="profile-recommendations">
                    <h2 className="profile-recs-title">Công nghệ được gợi ý cho bạn</h2>
                    {loadingRecs ? (
                        <div className="recs-loading">Đang tải gợi ý...</div>
                    ) : (
                        <div className="recs-grid">
                            {recommendations.map((rec) => (
                                <div key={rec.tech_name} className="rec-card">
                                    <div className="rec-header">
                                        <span className="rec-name">{rec.tech_name}</span>
                                        {rec.ring && (
                                            <span className={`rec-ring rec-ring--${rec.ring.toLowerCase()}`}>
                                                {rec.ring}
                                            </span>
                                        )}
                                    </div>
                                    {rec.reason && (
                                        <p className="rec-reason">{rec.reason}</p>
                                    )}
                                    <div className="rec-meta">
                                        {rec.growth_rate != null && (
                                            <span className={`rec-growth ${rec.growth_rate >= 0 ? 'up' : 'down'}`}>
                                                {rec.growth_rate >= 0 ? '+' : ''}{Number(rec.growth_rate).toFixed(1)}%
                                            </span>
                                        )}
                                        {rec.co_occurrence > 0 && (
                                            <span className="rec-cooc">Co-use: {rec.co_occurrence}</span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
