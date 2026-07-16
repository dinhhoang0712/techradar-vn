import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { loginUser, getCurrentUser, getSystemStatus, forgotPassword, resetPassword } from '../../api/authService';
import Modal from '../../components/common/Modal';
import { useToast } from '../../components/common/toastContext';
import './Auth.css';

export default function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [authModal, setAuthModal] = useState(null); // null | 'forgot' | 'reset'
    const [forgotEmail, setForgotEmail] = useState('');
    const [resetToken, setResetToken] = useState('');
    const [resetPwd, setResetPwd] = useState('');
    const [modalBusy, setModalBusy] = useState(false);
    const navigate = useNavigate();
    const notify = useToast();

    const openForgot = () => { setForgotEmail(email); setAuthModal('forgot'); };
    const openReset = () => { setResetToken(''); setResetPwd(''); setAuthModal('reset'); };
    const closeAuthModal = () => setAuthModal(null);

    const handleForgotSubmit = async (e) => {
        e.preventDefault();
        setModalBusy(true);
        try {
            await forgotPassword(forgotEmail);
            notify({ title: 'Nếu email tồn tại, mã đặt lại đã được gửi', body: 'Dùng "Nhập mã đặt lại" để đổi mật khẩu.', variant: 'success' });
            setAuthModal(null);
        } catch (err) {
            notify({ title: 'Không gửi được yêu cầu', body: err.message, variant: 'error' });
        } finally {
            setModalBusy(false);
        }
    };

    const handleResetSubmit = async (e) => {
        e.preventDefault();
        setModalBusy(true);
        try {
            await resetPassword(resetToken, resetPwd);
            notify({ title: 'Đổi mật khẩu thành công', body: 'Vui lòng đăng nhập lại.', variant: 'success' });
            setAuthModal(null);
        } catch (err) {
            notify({ title: 'Đặt lại mật khẩu thất bại', body: err.message || 'Mã sai hoặc đã hết hạn.', variant: 'error' });
        } finally {
            setModalBusy(false);
        }
    };

    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            // 1. Fetch system status (Don't block yet, we need to know the role first)
            let status = null;
            try {
                status = await getSystemStatus();
                if (status) {
                    localStorage.setItem('feature_graph', String(status.feature_graph));
                    localStorage.setItem('feature_rag', String(status.feature_rag));
                    localStorage.setItem('feature_chat', String(status.feature_chat));
                }
            } catch (e) {
                console.error('Failed to get system status', e);
            }

            // 2. Proceed with Login
            const res = await loginUser({ email, password });
            if (res.access_token) {
                localStorage.setItem('access_token', res.access_token);
                if (res.refresh_token) {
                    localStorage.setItem('refresh_token', res.refresh_token);
                }
                localStorage.setItem('login_timestamp', Date.now().toString());

                // Check user role for redirection and maintenance bypass
                let userRole = 'user';
                try {
                    const user = await getCurrentUser();
                    if (user && user.role) {
                        userRole = user.role;
                    }
                } catch (userError) {
                    console.error('Failed to fetch user info:', userError);
                }

                // If system is under maintenance, block non-admin users
                if (status && status.maintenance_web === true && userRole !== 'admin') {
                    localStorage.removeItem('access_token');
                    localStorage.removeItem('refresh_token');
                    localStorage.removeItem('login_timestamp');
                    setError('Hệ thống đang bảo trì phiên bản Web. Vui lòng quay lại sau.');
                    setLoading(false);
                    return;
                }

                if (userRole === 'admin') {
                    navigate('/admin');
                } else {
                    navigate('/dashboard');
                }
            } else {
                setError('Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.');
            }
        } catch {
            setError('Đăng nhập thất bại. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-left">
                <div className="auth-form-wrapper">
                    <div className="auth-logo">Tech<span>Radar</span></div>
                    <div>
                        <h1 className="auth-title">Chào mừng trở lại.</h1>
                        <p className="auth-subtitle">Đăng nhập để tiếp tục khai phá dữ liệu.</p>
                    </div>

                    {error && <div className="auth-error">{error}</div>}

                    <form className="auth-form" onSubmit={handleLogin}>
                        <div className="auth-input-group">
                            <label>Email Address</label>
                            <input 
                                type="email" 
                                required 
                                placeholder="name@company.com"
                                value={email}
                                onChange={e => setEmail(e.target.value)}
                            />
                        </div>
                        <div className="auth-input-group">
                            <label>Password</label>
                            <input 
                                type="password" 
                                required 
                                placeholder="••••••••"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                            />
                        </div>
                        
                        <div className="auth-link-row">
                            <button type="button" className="auth-link-btn" onClick={openForgot}>
                                Quên mật khẩu?
                            </button>
                            <button type="button" className="auth-link-btn" onClick={openReset}>
                                Nhập mã đặt lại
                            </button>
                        </div>

                        <button type="submit" className="auth-btn" disabled={loading}>
                            {loading ? 'Đang xác thực...' : 'Đăng nhập'}
                        </button>
                    </form>

                    <div className="auth-footer">
                        Chưa có tài khoản? 
                        <Link to="/register" className="auth-link">Đăng ký ngay</Link>
                    </div>
                </div>
            </div>

            <div className="auth-right">
                <div className="auth-orbit-decor" aria-hidden="true">
                    <div className="auth-orbit-ring">
                        <span className="auth-orbit-dot" style={{ color: 'var(--primary)', background: 'var(--primary)', transform: 'rotate(0deg) translate(140px) rotate(0deg)' }} />
                        <span className="auth-orbit-dot" style={{ color: 'var(--accent)', background: 'var(--accent)', transform: 'rotate(140deg) translate(140px) rotate(0deg)' }} />
                        <span className="auth-orbit-dot" style={{ color: 'var(--primary-light)', background: 'var(--primary-light)', transform: 'rotate(255deg) translate(140px) rotate(0deg)' }} />
                    </div>
                </div>
                <h2 className="auth-artwork-title">Phân tích.<br/>Làm chủ.<br/>Dẫn đầu.</h2>
                <p className="auth-artwork-subtitle">
                    Hệ thống trích xuất và phân tích xu hướng công nghệ TechRadar - Mang lợi thế cạnh tranh vào lòng bàn tay bạn.
                </p>
                <div className="auth-stats-row">
                    <span className="auth-stat-chip">500+ công ty</span>
                    <span className="auth-stat-chip">10k+ báo cáo</span>
                    <span className="auth-stat-chip">1000+ tin tuyển dụng</span>
                </div>
            </div>

            {authModal === 'forgot' && (
                <Modal title="Quên mật khẩu" onClose={closeAuthModal} width="380px">
                    <form className="modal-form" onSubmit={handleForgotSubmit}>
                        <p className="modal-body-text">
                            Nhập email tài khoản, chúng tôi sẽ gửi mã đặt lại mật khẩu.
                        </p>
                        <div className="form-group">
                            <label>Email</label>
                            <input
                                required
                                type="email"
                                autoFocus
                                value={forgotEmail}
                                onChange={(e) => setForgotEmail(e.target.value)}
                                placeholder="name@company.com"
                            />
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn btn-ghost" onClick={closeAuthModal} disabled={modalBusy}>Hủy bỏ</button>
                            <button type="submit" className="btn btn-primary" disabled={modalBusy}>
                                {modalBusy ? 'Đang gửi…' : 'Gửi mã đặt lại'}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}

            {authModal === 'reset' && (
                <Modal title="Nhập mã đặt lại" onClose={closeAuthModal} width="380px">
                    <form className="modal-form" onSubmit={handleResetSubmit}>
                        <div className="form-group">
                            <label>Mã đặt lại (token)</label>
                            <input
                                required
                                type="text"
                                autoFocus
                                value={resetToken}
                                onChange={(e) => setResetToken(e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label>Mật khẩu mới (tối thiểu 8 ký tự)</label>
                            <input
                                required
                                type="password"
                                minLength={8}
                                value={resetPwd}
                                onChange={(e) => setResetPwd(e.target.value)}
                            />
                        </div>
                        <div className="modal-actions">
                            <button type="button" className="btn btn-ghost" onClick={closeAuthModal} disabled={modalBusy}>Hủy bỏ</button>
                            <button type="submit" className="btn btn-primary" disabled={modalBusy}>
                                {modalBusy ? 'Đang đổi…' : 'Đổi mật khẩu'}
                            </button>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
