import type { Dispatch, SetStateAction, FormEvent } from 'react';
import PasswordInput from '../common/PasswordInput';

export interface ProfileFormState {
    full_name: string;
    email: string;
    avatar_url: string;
    bio: string;
    job_role: string;
    location: string;
    password: string;
    technologies: string;
    notify_inapp: boolean;
    notify_email: boolean;
}

interface ProfileEditFormProps {
    form: ProfileFormState;
    setForm: Dispatch<SetStateAction<ProfileFormState>>;
    saving: boolean;
    onSubmit: (e: FormEvent) => void;
    onCancel: () => void;
}

// Form chỉnh sửa hồ sơ cá nhân — nhận thẳng `form`/`setForm` từ UserProfile vì đây là state cục bộ
// chỉ dùng trong 1 form, không cần bọc qua các handler riêng cho từng field.
export default function ProfileEditForm({ form, setForm, saving, onSubmit, onCancel }: ProfileEditFormProps) {
    return (
        <form className="profile-form" onSubmit={onSubmit}>
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
                <PasswordInput
                    id="password"
                    className="form-input"
                    value={form.password || ''}
                    onChange={(e) => setForm(prev => ({ ...prev, password: e.target.value }))}
                    placeholder="Nhập mật khẩu mới"
                />
            </div>

            <div className="form-group">
                <label className="form-label">Thông báo</label>
                <div className="notif-pref">
                    <label className="switch">
                        <input
                            type="checkbox"
                            checked={form.notify_inapp}
                            onChange={(e) => setForm(prev => ({ ...prev, notify_inapp: e.target.checked }))}
                        />
                        <span className="slider" />
                    </label>
                    <div className="notif-pref-text">
                        <span>Thông báo trong ứng dụng</span>
                        <p>Hiện thông báo trên biểu tượng chuông và trang "Thông báo" khi có cập nhật mới.</p>
                    </div>
                </div>
                <div className="notif-pref">
                    <label className="switch">
                        <input
                            type="checkbox"
                            checked={form.notify_email}
                            onChange={(e) => setForm(prev => ({ ...prev, notify_email: e.target.checked }))}
                        />
                        <span className="slider" />
                    </label>
                    <div className="notif-pref-text">
                        <span>Thông báo qua email</span>
                        <p>Gửi email tóm tắt khi có xu hướng công nghệ hoặc cập nhật liên quan đến bạn.</p>
                    </div>
                </div>
            </div>

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
                    onClick={onCancel}
                    disabled={saving}
                    id="cancel-edit-btn"
                >
                    Huỷ
                </button>
            </div>
        </form>
    );
}
