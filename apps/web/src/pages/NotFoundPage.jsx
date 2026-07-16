import { useNavigate } from 'react-router-dom';
import './NotFoundPage.css';

export default function NotFoundPage() {
    const navigate = useNavigate();

    return (
        <div className="notfound-wrapper">
            <div className="notfound-content">
                <div className="radar-ping-visual" aria-hidden="true">
                    <span className="radar-ping-ring radar-ping-ring-1"></span>
                    <span className="radar-ping-ring radar-ping-ring-2"></span>
                    <span className="radar-ping-ring radar-ping-ring-3"></span>
                    <span className="radar-ping-dot"></span>
                </div>
                <div className="notfound-code">404</div>
                <h1>Không Tìm Thấy Tín Hiệu</h1>
                <p>Trang bạn tìm không tồn tại hoặc đã bị di chuyển sang một vị trí khác trên radar.</p>

                <div style={{ marginBottom: '30px' }}>
                    <button
                        className="btn btn-primary"
                        onClick={() => navigate('/dashboard')}
                        style={{ padding: '12px 32px', fontSize: '1rem', fontWeight: '600' }}
                    >
                        Quay lại Trang chủ
                    </button>
                </div>

            </div>
        </div>
    );
}
