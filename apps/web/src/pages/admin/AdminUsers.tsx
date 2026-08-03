import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { fetchAdminUsers, createAdminUser, updateAdminUser, deleteAdminUser, sendAdminNotification } from '../../api/adminService';
import type { AdminUser } from '../../types/admin';
import Modal from '../../components/common/Modal';
import Avatar from '../../components/common/Avatar';
import PasswordInput from '../../components/common/PasswordInput';
import { useToast } from '../../components/common/toastContext';
import './AdminUsers.css';

type NotifyTarget = 'broadcast' | AdminUser | null;

export default function AdminUsers() {
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [loading, setLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editTarget, setEditTarget] = useState<string | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null);
    const [saving, setSaving] = useState(false);
    const notify = useToast();

    // Gửi thông báo: notifyTarget === 'broadcast' | { id, full_name, ... } (1 user) | null (đóng)
    const [notifyTarget, setNotifyTarget] = useState<NotifyTarget>(null);
    const [notifyTitle, setNotifyTitle] = useState('');
    const [notifyBody, setNotifyBody] = useState('');
    const [notifyLink, setNotifyLink] = useState('');
    const [sendingNotify, setSendingNotify] = useState(false);

    // Form states
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [role, setRole] = useState('user');
    const [status, setStatus] = useState('active');

    useEffect(() => {
        loadUsers();
        // Mount-only: loadUsers is recreated every render, adding it here would refetch on every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadUsers = async () => {
        try {
            setLoading(true);
            const res = await fetchAdminUsers();
            // Swagger shows response is { data: [...] }
            if (res && res.data) {
                setUsers(res.data);
            } else if (Array.isArray(res)) {
                // Fallback if it returns array directly
                setUsers(res);
            }
        } catch (error) {
            console.error('Failed to load users:', error);
            notify({ title: 'Không tải được danh sách người dùng', variant: 'error' });
        } finally {
            setLoading(false);
        }
    };

    const handleOpenAdd = () => {
        setEditTarget(null);
        setName(''); setEmail(''); setPassword(''); setRole('user'); setStatus('active');
        setIsModalOpen(true);
    };

    const handleOpenEdit = (user: AdminUser) => {
        setEditTarget(user.id);
        setName(user.full_name || user.name || '');
        setEmail(user.email || '');
        setRole(user.role ? String(user.role).toLowerCase() : 'user');
        setStatus(user.status ? String(user.status).toLowerCase() : 'active');
        setIsModalOpen(true);
    };

    const handleDelete = async () => {
        if (!deleteTarget) return;
        try {
            await deleteAdminUser(deleteTarget.id);
            setUsers(prev => prev.filter(u => u.id !== deleteTarget.id));
            notify({ title: 'Đã xoá tài khoản', variant: 'success' });
        } catch (error) {
            console.error('Failed to delete user:', error);
            notify({ title: 'Không thể xoá người dùng', variant: 'error' });
        } finally {
            setDeleteTarget(null);
        }
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            if (editTarget) {
                // PUT /admin/users/{id} — body: { full_name, password, role, status }
                const editPayload: { full_name: string; role: string; status: string; password?: string } = { full_name: name, role, status };
                if (password) editPayload.password = password;
                const res = await updateAdminUser(editTarget, editPayload);
                // Cập nhật local state với data trả về từ server (hoặc payload)
                const updated = res?.data || editPayload;
                setUsers(prev => prev.map(u =>
                    u.id === editTarget ? { ...u, ...updated } : u
                ));
                notify({ title: 'Cập nhật tài khoản thành công', variant: 'success' });
            } else {
                // POST /admin/users — body: { email, full_name, role, status, password }
                const createPayload = { email, full_name: name, role, status, password };
                await createAdminUser(createPayload);
                // Refresh để lấy ID thực từ backend
                await loadUsers();
                notify({ title: 'Tạo tài khoản thành công', variant: 'success' });
            }
            setIsModalOpen(false);
        } catch (error) {
            console.error('Failed to save user:', error);
            notify({ title: 'Lỗi khi lưu thông tin người dùng', body: (error as Error).message || 'Vui lòng kiểm tra lại.', variant: 'error' });
        } finally {
            setSaving(false);
        }
    };

    const openBroadcast = () => {
        setNotifyTitle(''); setNotifyBody(''); setNotifyLink('');
        setNotifyTarget('broadcast');
    };

    const openNotifyUser = (user: AdminUser) => {
        setNotifyTitle(''); setNotifyBody(''); setNotifyLink('');
        setNotifyTarget(user);
    };

    const handleSendNotification = async (e: FormEvent) => {
        e.preventDefault();
        setSendingNotify(true);
        try {
            const res = await sendAdminNotification({
                title: notifyTitle,
                body: notifyBody,
                link: notifyLink || undefined,
                userId: notifyTarget === 'broadcast' ? undefined : notifyTarget?.id,
            });
            const recipients = res?.data?.recipients ?? 0;
            notify({
                title: notifyTarget === 'broadcast'
                    ? `Đã gửi thông báo tới ${recipients} người dùng`
                    : 'Đã gửi thông báo',
                variant: 'success',
            });
            setNotifyTarget(null);
        } catch (error) {
            console.error('Failed to send admin notification:', error);
            notify({ title: 'Gửi thông báo thất bại', body: (error as Error).message, variant: 'error' });
        } finally {
            setSendingNotify(false);
        }
    };

    return (
        <div className="admin-users">
            <div className="users-header">
                <div className="users-title">
                    <h2>Quản lý Người dùng</h2>
                    <p>Hỗ trợ thao tác tạo, khoá, chỉnh sửa và phân quyền tài khoản hệ thống qua API.</p>
                </div>
                <div className="users-actions">
                    <button className="btn btn-secondary" onClick={openBroadcast}>📢 Gửi thông báo</button>
                    <button className="btn btn-primary" onClick={handleOpenAdd}>Thêm tài khoản</button>
                </div>
            </div>

            <div className="users-card card">
                <table className="users-table">
                    <thead>
                        <tr>
                            <th>Họ và Tên</th>
                            <th>Email</th>
                            <th>Vai trò</th>
                            <th>Trạng thái</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && (
                            <tr><td colSpan={5} className="users-state-cell">Đang tải danh sách người dùng…</td></tr>
                        )}
                        {!loading && users.length === 0 && (
                            <tr><td colSpan={5} className="users-state-cell">Chưa có người dùng nào</td></tr>
                        )}
                        {!loading && users.map(u => (
                            <tr key={u.id}>
                                <td>
                                    <div className="u-identity">
                                        <Avatar user={{ full_name: u.full_name || u.name || u.email, avatar_url: u.avatar_url }} size={36} />
                                        <span>{u.full_name || u.name}</span>
                                    </div>
                                </td>
                                <td>{u.email}</td>
                                <td>
                                    <span className={`role-badge ${String(u.role).toLowerCase() === 'admin' ? 'admin' : 'user'}`}>
                                        {u.role}
                                    </span>
                                </td>
                                <td>
                                    <span className={`status-badge ${String(u.status).toLowerCase() === 'active' ? 'active' : 'blocked'}`}>
                                        {u.status}
                                    </span>
                                </td>
                                <td className="u-actions">
                                    <button className="u-btn notify" onClick={() => openNotifyUser(u)}>Thông báo</button>
                                    <button className="u-btn edit" onClick={() => handleOpenEdit(u)}>Sửa</button>
                                    <button className="u-btn del" onClick={() => setDeleteTarget(u)}>Xoá</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {isModalOpen && (
                <Modal title={editTarget ? 'Chỉnh sửa tài khoản' : 'Tạo mới tài khoản'} onClose={() => setIsModalOpen(false)}>
                    <form onSubmit={handleSubmit} className="modal-form">
                        <div className="form-group">
                            <label>Họ và Tên</label>
                            <input required type="text" value={name} onChange={e => setName(e.target.value)} />
                        </div>
                        <div className="form-group">
                            <label>Email</label>
                            <input required={!editTarget} disabled={!!editTarget} type="email" value={email} onChange={e => setEmail(e.target.value)} />
                        </div>
                        <div className="form-group">
                            <label>{editTarget ? 'Mật khẩu mới' : 'Mật khẩu'}</label>
                            <PasswordInput required={!editTarget} value={password} onChange={e => setPassword(e.target.value)} placeholder={editTarget ? "Bỏ trống nếu không đổi" : ""} />
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <label>Vai trò</label>
                                <select value={role} onChange={e => setRole(e.target.value)}>
                                    <option value="user">User</option>
                                    <option value="admin">Admin</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Trạng thái</label>
                                <select value={status} onChange={e => setStatus(e.target.value)}>
                                    <option value="active">Active</option>
                                    <option value="blocked">Blocked</option>
                                </select>
                            </div>
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn btn-ghost" onClick={() => setIsModalOpen(false)} disabled={saving}>Hủy bỏ</button>
                            <button type="submit" className="btn btn-primary" disabled={saving}>
                                {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}

            {deleteTarget && (
                <Modal title="Xác nhận xoá" onClose={() => setDeleteTarget(null)} width="380px">
                    <p className="modal-body-text">
                        Bạn có chắc chắn muốn xoá tài khoản "{deleteTarget.full_name || deleteTarget.name}"? Hành động này không thể hoàn tác.
                    </p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDelete}>Xoá</button>
                    </div>
                </Modal>
            )}

            {notifyTarget && (
                <Modal
                    title={notifyTarget === 'broadcast' ? 'Gửi thông báo tới tất cả người dùng' : `Gửi thông báo tới ${notifyTarget.full_name || notifyTarget.name}`}
                    onClose={() => setNotifyTarget(null)}
                >
                    <form onSubmit={handleSendNotification} className="modal-form">
                        <p className="notify-target-hint">
                            {notifyTarget === 'broadcast'
                                ? 'Thông báo sẽ được gửi tới mọi tài khoản đang ở trạng thái Active.'
                                : `Chỉ gửi riêng cho ${notifyTarget.email}.`}
                        </p>
                        <div className="form-group">
                            <label>Tiêu đề</label>
                            <input required type="text" value={notifyTitle} onChange={e => setNotifyTitle(e.target.value)} placeholder="Ví dụ: Bảo trì hệ thống" />
                        </div>
                        <div className="form-group">
                            <label>Nội dung</label>
                            <textarea required rows={4} value={notifyBody} onChange={e => setNotifyBody(e.target.value)} placeholder="Nội dung thông báo…" />
                        </div>
                        <div className="form-group">
                            <label>Đường dẫn (tuỳ chọn)</label>
                            <input type="text" value={notifyLink} onChange={e => setNotifyLink(e.target.value)} placeholder="/dashboard" />
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn btn-ghost" onClick={() => setNotifyTarget(null)} disabled={sendingNotify}>Hủy bỏ</button>
                            <button type="submit" className="btn btn-primary" disabled={sendingNotify}>
                                {sendingNotify ? 'Đang gửi…' : 'Gửi thông báo'}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
