import './MaintenancePage.css';

interface MaintenancePageProps {
    message?: string;
}

export default function MaintenancePage({ message }: MaintenancePageProps) {
    return (
        <div className="maintenance-wrapper">
            <div className="maintenance-content">
                <div className="radar-ping-visual" aria-hidden="true">
                    <span className="radar-ping-ring radar-ping-ring-1"></span>
                    <span className="radar-ping-ring radar-ping-ring-2"></span>
                    <span className="radar-ping-ring radar-ping-ring-3"></span>
                    <span className="radar-ping-dot"></span>
                </div>
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
