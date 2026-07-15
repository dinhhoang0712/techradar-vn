import { useEffect, useState, useCallback } from 'react';
import {
    fetchCmsContent,
    createCmsContent,
    updateCmsContent,
    deleteCmsContent,
} from '../../api/adminService';
import Modal from '../../components/common/Modal';
import { useToast } from '../../components/common/toastContext';
import './AdminCMS.css';

const today = () => new Date().toISOString().slice(0, 10);
const TYPES = ['Report', 'Job', 'Keyword'];
const STATUSES = ['Pending', 'Analyzed', 'Published', 'Archived'];

export default function AdminCMS() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [formTarget, setFormTarget] = useState(null); // null = closed, {} = new, item = edit
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [saving, setSaving] = useState(false);
    const notify = useToast();

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetchCmsContent();
            setData(res?.data ?? res ?? []);
        } catch (e) {
            setError(e.message || 'Không tải được dữ liệu CMS');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const openAdd = () => setFormTarget({ title: '', type: TYPES[0], status: STATUSES[0] });
    const openEdit = (item) => setFormTarget({ ...item });

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            if (formTarget.id) {
                await updateCmsContent(formTarget.id, { title: formTarget.title, status: formTarget.status });
                notify({ title: 'Cập nhật bản ghi thành công', variant: 'success' });
            } else {
                await createCmsContent({
                    title: formTarget.title,
                    type: formTarget.type,
                    status: formTarget.status,
                    content_date: today(),
                });
                notify({ title: 'Tạo bản ghi thành công', variant: 'success' });
            }
            setFormTarget(null);
            await load();
        } catch (e) {
            notify({ title: formTarget.id ? 'Cập nhật thất bại' : 'Tạo bản ghi thất bại', body: e.message, variant: 'error' });
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        try {
            await deleteCmsContent(deleteTarget.id);
            setDeleteTarget(null);
            notify({ title: 'Đã xoá bản ghi', variant: 'success' });
            await load();
        } catch (e) {
            notify({ title: 'Xoá thất bại', body: e.message, variant: 'error' });
        }
    };

    return (
        <div className="admin-cms">
            <div className="cms-header">
                <div className="cms-title">
                    <h2>Quản lý Nội dung & Dữ liệu (CMS)</h2>
                    <p>Quản lý các nguồn Crawler, Bài Report và Từ khoá Đào tạo của hệ thống TechRadar.</p>
                </div>
                <div className="cms-actions">
                    <button className="btn btn-primary" onClick={openAdd}>Thêm bản ghi</button>
                </div>
            </div>

            <div className="cms-card card">
                {error && <div className="cms-error">{error}</div>}
                <table className="cms-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Tiêu đề / Nguồn dữ liệu</th>
                            <th>Loại dữ liệu</th>
                            <th>Ngày cập nhật</th>
                            <th>Trạng thái AI</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && (
                            <tr><td colSpan={6} className="cms-state-cell">Đang tải…</td></tr>
                        )}
                        {!loading && data.length === 0 && (
                            <tr><td colSpan={6} className="cms-state-cell">Chưa có bản ghi nào</td></tr>
                        )}
                        {!loading && data.map(item => (
                            <tr key={item.id}>
                                <td className="c-id">#{String(item.id).slice(0, 8)}</td>
                                <td className="c-title">{item.title}</td>
                                <td><span className={`c-type type-${String(item.type || '').toLowerCase()}`}>{item.type}</span></td>
                                <td>{item.content_date || item.date || '-'}</td>
                                <td><span className={`c-status status-${String(item.status || '').toLowerCase()}`}>{item.status}</span></td>
                                <td className="c-actions">
                                    <button className="c-btn edit" onClick={() => openEdit(item)}>Sửa</button>
                                    <button className="c-btn del" onClick={() => setDeleteTarget(item)}>Xoá</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                <div className="cms-pagination">
                    <span>Tổng {data.length} dòng</span>
                </div>
            </div>

            {formTarget && (
                <Modal title={formTarget.id ? 'Chỉnh sửa bản ghi' : 'Thêm bản ghi'} onClose={() => setFormTarget(null)}>
                    <form className="modal-form" onSubmit={handleSubmit}>
                        <div className="form-group">
                            <label>Tiêu đề</label>
                            <input
                                required
                                type="text"
                                value={formTarget.title}
                                onChange={(e) => setFormTarget((f) => ({ ...f, title: e.target.value }))}
                            />
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <label>Loại dữ liệu</label>
                                <select
                                    value={formTarget.type}
                                    disabled={!!formTarget.id}
                                    onChange={(e) => setFormTarget((f) => ({ ...f, type: e.target.value }))}
                                >
                                    {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                                </select>
                            </div>
                            <div className="form-group">
                                <label>Trạng thái</label>
                                <select
                                    value={formTarget.status}
                                    onChange={(e) => setFormTarget((f) => ({ ...f, status: e.target.value }))}
                                >
                                    {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                                </select>
                            </div>
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn btn-ghost" onClick={() => setFormTarget(null)} disabled={saving}>Hủy bỏ</button>
                            <button type="submit" className="btn btn-primary" disabled={saving}>
                                {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}

            {deleteTarget && (
                <Modal title="Xác nhận xoá" onClose={() => setDeleteTarget(null)} width="380px">
                    <p className="modal-body-text">Bạn có chắc chắn muốn xoá "{deleteTarget.title}"? Hành động này không thể hoàn tác.</p>
                    <div className="modal-actions">
                        <button className="btn btn-ghost" onClick={() => setDeleteTarget(null)}>Hủy bỏ</button>
                        <button className="btn btn-danger" onClick={handleDelete}>Xoá</button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
