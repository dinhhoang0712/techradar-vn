import './MaintenancePage.css';

export default function MaintenancePage({ message }) {
    return (
        <div className="maintenance-wrapper">
            <div className="maintenance-content">
                <div className="icon-pulse">🚧</div>
                <h1>Hệ Thống Đang Bảo Trì</h1>
                <p>{message || 'Chúng tôi đang tiến hành bảo trì định kỳ. Vui lòng quay lại sau.'}</p>
                
                <div style={{ marginBottom: '30px' }}>
                    <button 
                        className="btn btn-primary" 
                        onClick={() => window.location.href = '/dashboard'}
                        style={{ padding: '12px 32px', fontSize: '1rem', fontWeight: '600' }}
                    >
                        Quay lại Trang chủ
                    </button>
                </div>

            </div>
        </div>
    );
}
