import { useState, useEffect } from 'react';
import { getCareerAdvice } from '../api/careerService';
import { getUserProfile } from '../api/userService';
import './CareerPage.css';

const COMMON_ROLES = [
    'Senior Backend Developer',
    'Senior Frontend Developer',
    'Full Stack Developer',
    'DevOps Engineer',
    'Data Engineer',
    'ML Engineer',
    'Cloud Architect',
    'Mobile Developer',
    'Security Engineer',
    'Tech Lead',
];

export default function CareerPage() {
    const [targetRole, setTargetRole] = useState('');
    const [currentSkills, setCurrentSkills] = useState('');
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [profileLoaded, setProfileLoaded] = useState(false);

    // Tải kỹ năng từ profile nếu có
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;
        getUserProfile()
            .then((res) => {
                const data = res?.data ?? res ?? {};
                const techs = data.profile?.technologies || data.technologies || [];
                if (techs.length > 0) {
                    setCurrentSkills(techs.join(', '));
                    setProfileLoaded(true);
                }
            })
            .catch(() => {});
    }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!targetRole.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const skills = currentSkills.split(',').map(s => s.trim()).filter(Boolean);
            const res = await getCareerAdvice(targetRole.trim(), skills);
            setResult(res?.data ?? res);
        } catch (err) {
            setError(err.message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="career-page">
            <div className="career-hero">
                <h1 className="career-title">Lộ trình nghề nghiệp</h1>
                <p className="career-subtitle">
                    Phân tích khoảng cách kỹ năng và nhận lộ trình học tập cá nhân hoá
                </p>
            </div>

            <div className="career-layout">
                {/* Form */}
                <div className="career-form-card card">
                    <h2 className="section-title">Thông tin của bạn</h2>
                    <form onSubmit={handleSubmit} className="career-form">
                        <div className="form-group">
                            <label className="form-label">Vai trò mục tiêu</label>
                            <div className="role-quick-picks">
                                {COMMON_ROLES.slice(0, 5).map(r => (
                                    <button
                                        key={r}
                                        type="button"
                                        className={`role-chip${targetRole === r ? ' active' : ''}`}
                                        onClick={() => setTargetRole(r)}
                                    >
                                        {r}
                                    </button>
                                ))}
                            </div>
                            <input
                                type="text"
                                className="form-input"
                                value={targetRole}
                                onChange={e => setTargetRole(e.target.value)}
                                placeholder="VD: Senior Backend Developer"
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label className="form-label">
                                Kỹ năng hiện có
                                {profileLoaded && <span className="label-hint"> (đã tải từ hồ sơ)</span>}
                            </label>
                            <textarea
                                className="form-input"
                                rows={3}
                                value={currentSkills}
                                onChange={e => setCurrentSkills(e.target.value)}
                                placeholder="VD: Python, Django, PostgreSQL, Docker"
                            />
                            <span className="form-hint">Phân tách bằng dấu phẩy</span>
                        </div>

                        <button
                            type="submit"
                            className="btn btn-primary career-submit-btn"
                            disabled={loading || !targetRole.trim()}
                        >
                            {loading ? (
                                <><span className="btn-spinner" /> Đang phân tích...</>
                            ) : 'Phân tích lộ trình'}
                        </button>
                    </form>

                    {error && <div className="career-error">{error}</div>}
                </div>

                {/* Result */}
                {result && (
                    <div className="career-result">
                        <div className="card career-summary-card">
                            <div className="career-result-header">
                                <div>
                                    <h2 className="section-title">Kết quả phân tích</h2>
                                    <p className="career-target-role">Mục tiêu: <strong>{result.target_role}</strong></p>
                                </div>
                                {result.estimated_months && (
                                    <div className="career-estimate-badge">
                                        ~{result.estimated_months} tháng
                                    </div>
                                )}
                            </div>

                            {result.current_skills?.length > 0 && (
                                <div className="career-skills-row">
                                    <span className="skills-label">Kỹ năng hiện tại:</span>
                                    <div className="skills-chips">
                                        {result.current_skills.map(s => (
                                            <span key={s} className="skill-chip skill-chip--have">{s}</span>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        {result.skill_gap?.length > 0 && (
                            <div className="card">
                                <h3 className="section-title">Kỹ năng cần học</h3>
                                <div className="skill-gap-table">
                                    {result.skill_gap.map((step) => (
                                        <div key={step.skill} className="skill-gap-row">
                                            <div className="skill-gap-priority">#{step.priority}</div>
                                            <div className="skill-gap-info">
                                                <span className="skill-gap-name">{step.skill}</span>
                                                <span className="skill-gap-reason">{step.reason}</span>
                                            </div>
                                            {step.job_demand != null && (
                                                <div className="skill-gap-demand">
                                                    {step.job_demand.toLocaleString()} jobs
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {result.roadmap && (
                            <div className="card">
                                <h3 className="section-title">Lộ trình học tập</h3>
                                <div
                                    className="career-roadmap-content"
                                    dangerouslySetInnerHTML={{
                                        __html: result.roadmap
                                            .replace(/\n\n/g, '</p><p>')
                                            .replace(/\n/g, '<br/>')
                                            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                                            .replace(/^## (.*)/gm, '<h3>$1</h3>')
                                            .replace(/^# (.*)/gm, '<h2>$1</h2>')
                                            .replace(/^- (.*)/gm, '<li>$1</li>')
                                    }}
                                />
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
