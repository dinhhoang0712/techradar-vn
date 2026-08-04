export interface ProfileViewData {
    full_name?: string;
    job_role?: string;
    current_level?: string;
    bio?: string;
    location?: string;
    technologies?: string[];
    notify_inapp?: boolean;
    notify_email?: boolean;
}

interface ProfileViewDetailsProps {
    profile: ProfileViewData;
    onEdit: () => void;
}

// Chế độ xem (không chỉnh sửa) của hồ sơ cá nhân.
export default function ProfileViewDetails({ profile, onEdit }: ProfileViewDetailsProps) {
    return (
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
                <label className="form-label">Cấp độ hiện tại</label>
                <p className="form-value">{profile.current_level || <span className="form-empty">Chưa xác định</span>}</p>
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
                    onClick={onEdit}
                    id="edit-profile-btn"
                >
                    Chỉnh sửa thông tin
                </button>
            </div>
        </div>
    );
}
