import { useEffect, useState, useCallback } from 'react';
import {
    fetchCmsContent,
    createCmsContent,
    updateCmsContent,
    deleteCmsContent,
} from '../../api/adminService';
import './AdminCMS.css';

const today = () => new Date().toISOString().slice(0, 10);

export default function AdminCMS() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

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

    const handleAdd = async () => {
        const title = window.prompt('Tiêu đề:');
        if (!title) return;
        const type = window.prompt('Loại (Report / Job / Keyword):', 'Report') || 'Report';
        const status = window.prompt('Trạng thái (Pending / Analyzed / Published / Archived):', 'Pending') || 'Pending';
        try {
            await createCmsContent({ title, type, status, content_date: today() });
            await load();
        } catch (e) {
            alert(e.message || 'Tạo bản ghi thất bại');
        }
    };

    const handleEdit = async (item) => {
        const title = window.prompt('Tiêu đề:', item.title);
        if (title === null) return;
        const status = window.prompt('Trạng thái:', item.status) ?? item.status;
        try {
            await updateCmsContent(item.id, { title, status });
            await load();
        } catch (e) {
            alert(e.message || 'Cập nhật thất bại');
        }
    };

    const handleDelete = async (item) => {
        if (!window.confirm(`Xoá "${item.title}"?`)) return;
        try {
            await deleteCmsContent(item.id);
            await load();
        } catch (e) {
            alert(e.message || 'Xoá thất bại');
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
                    <button className="btn-add" onClick={handleAdd}>Thêm bản ghi</button>
                </div>
            </div>

            <div className="cms-card">
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
                            <tr><td colSpan={6} style={{ textAlign: 'center' }}>Đang tải…</td></tr>
                        )}
                        {!loading && data.length === 0 && (
                            <tr><td colSpan={6} style={{ textAlign: 'center' }}>Chưa có bản ghi nào</td></tr>
                        )}
                        {!loading && data.map(item => (
                            <tr key={item.id}>
                                <td className="c-id">#{String(item.id).slice(0, 8)}</td>
                                <td className="c-title">{item.title}</td>
                                <td><span className={`c-type type-${String(item.type || '').toLowerCase()}`}>{item.type}</span></td>
                                <td>{item.content_date || item.date || '-'}</td>
                                <td><span className={`c-status status-${String(item.status || '').toLowerCase()}`}>{item.status}</span></td>
                                <td className="c-actions">
                                    <button className="c-btn edit" onClick={() => handleEdit(item)}>Sửa</button>
                                    <button className="c-btn del" onClick={() => handleDelete(item)}>Xoá</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                <div className="cms-pagination">
                    <span>Tổng {data.length} dòng</span>
                </div>
            </div>
        </div>
    );
}
