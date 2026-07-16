import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { registerUser } from '../../api/authService';
import { useToast } from '../../components/common/toastContext';
import './Auth.css';

const MIN_PASSWORD_LENGTH = 8;

function resolveRegisterError(err) {
    const status = err?.status;
    const message = String(err?.message || '').trim();
    const lower = message.toLowerCase();

    if (status === 409 || lower.includes('already registered') || lower.includes('email already')) {
        return 'Email đã tồn tại. Vui lòng dùng email khác.';
    }
    if (status === 400 && (lower.includes('password') || lower.includes('8'))) {
        return `Mật khẩu phải có ít nhất ${MIN_PASSWORD_LENGTH} ký tự.`;
    }
    if (status === 400) {
        return message && message !== 'Bad Request'
            ? message
            : 'Thông tin đăng ký không hợp lệ. Vui lòng kiểm tra lại.';
    }
    if (err?.message === 'SERVER_CONNECTION_FAILED') {
        return 'Không kết nối được máy chủ. Vui lòng thử lại sau.';
    }
    return message || 'Đăng ký thất bại. Vui lòng thử lại.';
}

export default function RegisterPage() {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPwd, setConfirmPwd] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();
    const notify = useToast();

    const handleRegister = async (e) => {
        e.preventDefault();
        setError('');

        if (password.length < MIN_PASSWORD_LENGTH) {
            setError(`Mật khẩu phải có ít nhất ${MIN_PASSWORD_LENGTH} ký tự.`);
            return;
        }

        if (password !== confirmPwd) {
            setError('Mật khẩu xác nhận không khớp!');
            return;
        }

        setLoading(true);

        try {
            const res = await registerUser({ full_name: name, email, password, confirm_password: confirmPwd });
            if (res.status === 'success' || res.access_token) {
                notify({ title: 'Khởi tạo tài khoản thành công! Vui lòng đăng nhập.', variant: 'success' });
                navigate('/login');
            } else {
                setError('Đăng ký thất bại. Vui lòng thử lại.');
            }
        } catch (err) {
            setError(resolveRegisterError(err));
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
                        <h1 className="auth-title">Tham gia ngay.</h1>
                        <p className="auth-subtitle">Mở ra không gian tri thức không giới hạn.</p>
                    </div>

                    {error && <div className="auth-error">{error}</div>}

                    <form className="auth-form" onSubmit={handleRegister}>
                        <div className="auth-input-group">
                            <label>Họ và Tên</label>
                            <input 
                                type="text" 
                                required 
                                placeholder="Nguyen Van A"
                                value={name}
                                onChange={e => setName(e.target.value)}
                            />
                        </div>
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
                                placeholder="Ít nhất 8 ký tự"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                minLength={MIN_PASSWORD_LENGTH}
                            />
                        </div>
                        <div className="auth-input-group">
                            <label>Confirm Password</label>
                            <input 
                                type="password" 
                                required 
                                placeholder="Nhập lại mật khẩu"
                                value={confirmPwd}
                                onChange={e => setConfirmPwd(e.target.value)}
                                minLength={MIN_PASSWORD_LENGTH}
                            />
                        </div>
                        
                        <button type="submit" className="auth-btn" disabled={loading}>
                            {loading ? 'Đang khởi tạo...' : 'Tạo tài khoản mới'}
                        </button>
                    </form>

                    <div className="auth-footer">
                        Đã có tài khoản? 
                        <Link to="/login" className="auth-link">Đăng nhập ngay</Link>
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
                <h2 className="auth-artwork-title">Bứt phá <br/>Mọi Giới Hạn</h2>
                <p className="auth-artwork-subtitle">
                    Gia nhập cộng đồng người dùng TechRadar để tiếp cận các dữ liệu tuyển dụng và báo cáo chi tiết nhất.
                </p>
                <div className="auth-stats-row">
                    <span className="auth-stat-chip">500+ công ty</span>
                    <span className="auth-stat-chip">10k+ báo cáo</span>
                    <span className="auth-stat-chip">1000+ tin tuyển dụng</span>
                </div>
            </div>
        </div>
    );
}
