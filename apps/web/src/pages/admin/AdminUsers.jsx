import { useState, useEffect } from 'react';
import { fetchAdminUsers, createAdminUser, updateAdminUser, deleteAdminUser } from '../../api/adminService';
import './AdminUsers.css';

export default function AdminUsers() {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editTarget, setEditTarget] = useState(null);

    // Form states
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [role, setRole] = useState('user');
    const [status, setStatus] = useState('active');

    useEffect(() => {
        loadUsers();
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
        } finally {
            setLoading(false);
        }
    };

    const handleOpenAdd = () => {
        setEditTarget(null);
        setName(''); setEmail(''); setPassword(''); setRole('user'); setStatus('active');
        setIsModalOpen(true);
    };

    const handleOpenEdit = (user) => {
        setEditTarget(user.id);
        setName(user.full_name || user.name); 
        setEmail(user.email); 
        setRole(user.role ? String(user.role).toLowerCase() : 'user'); 
        setStatus(user.status ? String(user.status).toLowerCase() : 'active');
        setIsModalOpen(true);
    };

    const handleDelete = async (id) => {
        if(window.confirm('Bạn có chắc chắn muốn xoá người dùng này không?')) {
            try {
                await deleteAdminUser(id);
                setUsers(prev => prev.filter(u => u.id !== id));
            } catch (error) {
                console.error('Failed to delete user:', error);
                alert('Không thể xoá người dùng.');
            }
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            if (editTarget) {
                // PUT /admin/users/{id} — body: { full_name, password, role, status }
                const editPayload = { full_name: name, role, status };
                if (password) editPayload.password = password;
                const res = await updateAdminUser(editTarget, editPayload);
                // Cập nhật local state với data trả về từ server (hoặc payload)
                const updated = res?.data || res || editPayload;
                setUsers(prev => prev.map(u =>
                    u.id === editTarget ? { ...u, ...updated } : u
                ));
            } else {
                // POST /admin/users — body: { email, full_name, role, status, password }
                const createPayload = { email, full_name: name, role, status, password };
                await createAdminUser(createPayload);
                // Refresh để lấy ID thực từ backend
                await loadUsers();
            }
            setIsModalOpen(false);
        } catch (error) {
            console.error('Failed to save user:', error);
            alert(`Lỗi khi lưu thông tin người dùng: ${error.message || 'Vui lòng kiểm tra lại.'}`);
        }
    };

    if (loading && users.length === 0) return <div className="loading" style={{color: '#fff', textAlign: 'center', padding: '2rem'}}>Đang tải danh sách người dùng...</div>;

    return (
        <div className="admin-users">
            <div className="users-header">
                <div className="users-title">
                    <h2>Quản lý Người dùng</h2>
                    <p>Hỗ trợ thao tác tạo, khoá, chỉnh sửa và phân quyền tài khoản hệ thống qua API.</p>
                </div>
                <div className="users-actions">
                    <button className="btn-add" onClick={handleOpenAdd}>Thêm tài khoản</button>
                </div>
            </div>

            <div className="users-card">
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
                        {users.map(u => (
                            <tr key={u.id}>
                                <td>{u.full_name || u.name}</td>
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
                                    <button className="u-btn edit" onClick={() => handleOpenEdit(u)}>Sửa</button>
                                    <button className="u-btn del" onClick={() => handleDelete(u.id)}>Xoá</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Modal */}
            {isModalOpen && (
                <div className="modal-overlay" onClick={() => setIsModalOpen(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <h3>{editTarget ? 'Chỉnh sửa tài khoản' : 'Tạo mới tài khoản'}</h3>
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
                                <input required={!editTarget} type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder={editTarget ? "Bỏ trống nếu không đổi" : ""} />
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
                                <button type="button" className="m-btn cancel" onClick={() => setIsModalOpen(false)}>Hủy bỏ</button>
                                <button type="submit" className="m-btn save">Lưu thay đổi</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
