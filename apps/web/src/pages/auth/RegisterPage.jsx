import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { registerMock } from '../../api/authService';
import './Auth.css';

export default function RegisterPage() {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPwd, setConfirmPwd] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleRegister = async (e) => {
        e.preventDefault();
        setError('');

        if (password !== confirmPwd) {
            setError('Mật khẩu xác nhận không khớp!');
            return;
        }

        setLoading(true);

        try {
            // NOTE: Thay bằng API thực: registerUser (khi backend có sẵn)
            const res = await registerMock({ full_name: name, email, password, confirm_password: confirmPwd });
            if (res.status === 'success' || res.access_token) {
                alert('Khởi tạo tài khoản thành công! Vui lòng đăng nhập.');
                navigate('/login'); 
            }
        } catch (err) {
            setError('Đăng ký thất bại. Email đã tồn tại.');
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
                        <h1 className="auth-title">Join us.</h1>
                        <p className="auth-subtitle">Mở ra không gian tri thức không giới hạn.</p>
                    </div>

                    {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', padding: '10px', background: 'rgba(255,107,107,0.1)', borderRadius: '6px' }}>{error}</div>}

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
                                placeholder="••••••••"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                minLength={6}
                            />
                        </div>
                        <div className="auth-input-group">
                            <label>Confirm Password</label>
                            <input 
                                type="password" 
                                required 
                                placeholder="••••••••"
                                value={confirmPwd}
                                onChange={e => setConfirmPwd(e.target.value)}
                            />
                        </div>
                        
                        <button type="submit" className="auth-btn" disabled={loading} style={{marginTop: '12px'}}>
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
                <h2 className="auth-artwork-title">Bứt phá <br/>Mọi Giới Hạn</h2>
                <p className="auth-artwork-subtitle">
                    Gia nhập cộng đồng người dùng TechRadar để tiếp cận các dữ liệu tuyển dụng và báo cáo chi tiết nhất.
                </p>
            </div>
        </div>
    );
}
