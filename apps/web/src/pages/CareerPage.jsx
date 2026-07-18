import { useState, useEffect } from 'react';
import { getCareerAdvice } from '../api/careerService';
import { getUserProfile } from '../api/userService';
import { getJobMatches } from '../api/jobService';
import { renderMarkdown } from '../utils/markdown';
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
    const [hasToken, setHasToken] = useState(false);

    const [jobMatches, setJobMatches] = useState(null);
    const [jobsLoading, setJobsLoading] = useState(false);
    const [jobsError, setJobsError] = useState('');
    const [jobLocation, setJobLocation] = useState('');
    const [jobMinSalary, setJobMinSalary] = useState('');

    // Tải kỹ năng từ profile nếu có
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;
        setHasToken(true);
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

    const loadJobMatches = async () => {
        setJobsLoading(true);
        setJobsError('');
        try {
            const res = await getJobMatches({
                location: jobLocation.trim() || undefined,
                minSalary: jobMinSalary ? Number(jobMinSalary) : undefined,
            });
            setJobMatches(res?.data ?? res ?? []);
        } catch (err) {
            setJobsError(err.message || 'Không thể tải job phù hợp. Vui lòng thử lại.');
        } finally {
            setJobsLoading(false);
        }
    };

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
                                        className={`chip role-chip${targetRole === r ? ' active' : ''}`}
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
                                <div className="skill-gap-timeline">
                                    {result.skill_gap.map((step, idx) => (
                                        <div key={step.skill} className="skill-gap-timeline-item">
                                            <div className="skill-gap-timeline-rail">
                                                <div className="skill-gap-marker">{step.priority}</div>
                                                {idx < result.skill_gap.length - 1 && <div className="skill-gap-connector" />}
                                            </div>
                                            <div className="skill-gap-row">
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
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {result.roadmap && (
                            <div className="card">
                                <h3 className="section-title">Lộ trình học tập</h3>
                                <div className="career-roadmap-content">
                                    {renderMarkdown(result.roadmap)}
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {hasToken && (
                <div className="card job-matches-card">
                    <div className="job-matches-header">
                        <div>
                            <h2 className="section-title">Job phù hợp với hồ sơ của bạn</h2>
                            <p className="career-target-role">
                                Xếp hạng theo % kỹ năng trong hồ sơ trùng với yêu cầu tin tuyển dụng
                            </p>
                        </div>
                        <button
                            type="button"
                            className="btn btn-primary"
                            onClick={loadJobMatches}
                            disabled={jobsLoading}
                        >
                            {jobsLoading ? (
                                <><span className="btn-spinner" /> Đang tìm...</>
                            ) : 'Tìm job phù hợp'}
                        </button>
                    </div>

                    <div className="job-matches-filters">
                        <input
                            type="text"
                            className="form-input"
                            value={jobLocation}
                            onChange={e => setJobLocation(e.target.value)}
                            placeholder="Địa điểm (VD: Hà Nội)"
                        />
                        <input
                            type="number"
                            className="form-input"
                            value={jobMinSalary}
                            onChange={e => setJobMinSalary(e.target.value)}
                            placeholder="Lương tối thiểu (triệu VND)"
                            min="0"
                        />
                    </div>

                    {jobsError && <div className="career-error">{jobsError}</div>}

                    {jobMatches && jobMatches.length === 0 && !jobsError && (
                        <p className="job-matches-empty">
                            Không tìm thấy job phù hợp. Hãy thêm công nghệ quan tâm trong Hồ sơ cá nhân
                            hoặc nới lỏng bộ lọc.
                        </p>
                    )}

                    {jobMatches && jobMatches.length > 0 && (
                        <div className="job-match-list">
                            {jobMatches.map((job, i) => (
                                <div key={`${job.title}-${i}`} className="job-match-row">
                                    <div className="job-match-score">{Math.round(job.score * 100)}%</div>
                                    <div className="job-match-info">
                                        <div className="job-match-title-row">
                                            <span className="job-match-title">{job.title}</span>
                                            {job.source_url && (
                                                <a
                                                    href={job.source_url}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="job-match-link"
                                                >
                                                    Xem tin gốc ↗
                                                </a>
                                            )}
                                        </div>
                                        <div className="job-match-meta">
                                            {[job.company, job.location].filter(Boolean).join(' · ') || 'Chưa rõ nhà tuyển dụng'}
                                            {job.salary_min_mvnd != null && job.salary_max_mvnd != null
                                                ? ` · ${job.salary_min_mvnd}–${job.salary_max_mvnd} triệu VND`
                                                : job.salary_raw ? ` · ${job.salary_raw}` : ''}
                                        </div>
                                        <div className="skills-chips">
                                            {(job.matched_skills || []).map(s => (
                                                <span key={`m-${s}`} className="skill-chip skill-chip--have">{s}</span>
                                            ))}
                                            {(job.missing_skills || []).map(s => (
                                                <span key={`x-${s}`} className="skill-chip skill-chip--missing">{s}</span>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
