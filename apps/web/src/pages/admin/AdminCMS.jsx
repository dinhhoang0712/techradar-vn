import { useState } from 'react';
import './AdminCMS.css';

const MOCK_DATA = [
    { id: 1, title: 'Báo cáo: Xu hướng AI/ML 2026', type: 'Report', date: '2026-03-25', status: 'Published' },
    { id: 2, title: 'JD Senior Node.js - NAB', type: 'Job', date: '2026-04-12', status: 'Analyzed' },
    { id: 3, title: 'Từ khoá: "LangChain"', type: 'Keyword', date: '2026-04-16', status: 'Pending' },
    { id: 4, title: 'JD Front-End React 5 năm kinh nghiệm', type: 'Job', date: '2026-04-17', status: 'Analyzed' },
    { id: 5, title: 'Báo cáo: Lương IT Quý 1', type: 'Report', date: '2026-01-30', status: 'Archived' },
];

export default function AdminCMS() {
    const [data] = useState(MOCK_DATA);

    return (
        <div className="admin-cms">
            <div className="cms-header">
                <div className="cms-title">
                    <h2>Quản lý Nội dung & Dữ liệu (CMS)</h2>
                    <p>Quản lý các nguồn Crawler, Bài Report và Từ khoá Đào tạo của hệ thống TechRadar.</p>
                </div>
                <div className="cms-actions">
                    <button className="btn-upload">Import JSON/CSV</button>
                    <button className="btn-add">Thêm bản ghi</button>
                </div>
            </div>

            <div className="cms-card">
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
                        {data.map(item => (
                            <tr key={item.id}>
                                <td className="c-id">#{item.id}</td>
                                <td className="c-title">{item.title}</td>
                                <td><span className={`c-type type-${item.type.toLowerCase()}`}>{item.type}</span></td>
                                <td>{item.date}</td>
                                <td><span className={`c-status status-${item.status.toLowerCase()}`}>{item.status}</span></td>
                                <td className="c-actions">
                                    <button className="c-btn edit">Sửa</button>
                                    <button className="c-btn del">Xoá</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                <div className="cms-pagination">
                    <span>Hiển thị 1 - 5 của 120 dòng</span>
                    <div className="page-btns">
                        <button disabled>Trước</button>
                        <button>Sau</button>
                    </div>
                </div>
            </div>
        </div>
    );
}
