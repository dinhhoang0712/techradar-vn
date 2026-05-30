import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { loginUser, getCurrentUser, getSystemStatus } from '../../api/authService';
import './Auth.css';

export default function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const navigate = useNavigate();

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
        } catch (err) {
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
                        <h1 className="auth-title">Welcome back.</h1>
                        <p className="auth-subtitle">Đăng nhập để tiếp tục khai phá dữ liệu.</p>
                    </div>

                    {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem' }}>{error}</div>}

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
                <h2 className="auth-artwork-title">Phân tích.<br/>Làm chủ.<br/>Dẫn đầu.</h2>
                <p className="auth-artwork-subtitle">
                    Hệ thống trích xuất và phân tích xu hướng công nghệ TechRadar - Mang lợi thế cạnh tranh vào lòng bàn tay bạn.
                </p>
            </div>
        </div>
    );
}
