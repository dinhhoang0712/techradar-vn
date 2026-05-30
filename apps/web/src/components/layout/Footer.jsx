import './Footer.css';

export default function Footer() {
    const year = new Date().getFullYear();

    return (
        <footer className="site-footer">
            <div className="footer-inner">
                <div className="footer-brand">
                    <span className="footer-logo">TechRadar</span>
                    <p className="footer-tagline">Theo dõi xu hướng công nghệ thông minh</p>
                </div>

                <div className="footer-links">
                    <div className="footer-col">
                        <span className="footer-col-title">Tính năng</span>
                        <a href="/dashboard">Radar Dashboard</a>
                        <a href="/compare">So sánh Tech</a>
                        <a href="/graph">Đồ thị quan hệ</a>
                        <a href="/chat">AI Tư vấn</a>
                    </div>
                    <div className="footer-col">
                        <span className="footer-col-title">Dữ liệu</span>
                        <a href="#">Nguồn dữ liệu</a>
                        <a href="#">Cập nhật realtime</a>
                        <a href="#">Báo cáo xu hướng</a>
                    </div>
                </div>
            </div>

            <div className="footer-bottom">
                <span>© {year} TechRadar · Dữ liệu cập nhật 1h trước</span>
                <div className="footer-bottom-status">
                    <span className="footer-dot" />
                    <span>Hệ thống hoạt động bình thường</span>
                </div>
            </div>
        </footer>
    );
}
