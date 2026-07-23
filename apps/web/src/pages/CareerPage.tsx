import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getCareerAdvice, getCareerRoadmap, simulateCareerMove } from '../api/careerService';
import { getJobMatches } from '../api/jobService';
import TechRecommendationCards from '../components/TechRecommendationCards';
import CareerSimulator from '../components/CareerSimulator';
import CareerResultPanel from '../components/career/CareerResultPanel';
import JobMatchesCard from '../components/career/JobMatchesCard';
import type { CareerAdvice, NextSkill, JobMatch } from '../types/career';
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
    // Deep link from a job-match notification (JobMatchDispatcher's "/career?highlight=..."):
    // highlights the "next skill to learn" card(s)/path this job matched, instead of dropping
    // the user on an unfiltered page.
    const [searchParams] = useSearchParams();
    const highlightSkills = (searchParams.get('highlight') || '').split(',').map(s => s.trim()).filter(Boolean);

    const [targetRole, setTargetRole] = useState('');
    const [currentSkills, setCurrentSkills] = useState('');
    const [result, setResult] = useState<CareerAdvice | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [profileLoaded, setProfileLoaded] = useState(false);
    const [hasToken, setHasToken] = useState(false);

    const [jobMatches, setJobMatches] = useState<JobMatch[] | null>(null);
    const [jobsLoading, setJobsLoading] = useState(false);
    const [jobsError, setJobsError] = useState('');
    const [jobLocation, setJobLocation] = useState('');
    const [jobMinSalary, setJobMinSalary] = useState('');

    const [nextSkills, setNextSkills] = useState<NextSkill[]>([]);
    const [roadmapLoading, setRoadmapLoading] = useState(false);
    const [roadmapError, setRoadmapError] = useState('');
    const [hasTechnologies, setHasTechnologies] = useState<boolean | null>(null);

    // Tự động tải lộ trình (gợi ý công nghệ + roadmap theo role + job phù hợp) qua GET
    // /career/roadmap thay vì phải điền form và bấm nút riêng ở từng phần.
    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (!token) return;
        setHasToken(true);
        loadRoadmap();
    }, []);

    const loadRoadmap = async () => {
        setRoadmapLoading(true);
        setRoadmapError('');
        try {
            const res = await getCareerRoadmap();
            const data = res.data ?? {};
            setHasTechnologies(!!data.has_technologies);
            if (data.has_technologies) {
                const techs = data.current_technologies || [];
                if (techs.length > 0) {
                    setCurrentSkills(techs.join(', '));
                    setProfileLoaded(true);
                }
                setNextSkills(data.next_skills || []);
                if (data.career_path?.target_role) {
                    setTargetRole(data.career_path.target_role);
                    setResult(data.career_path);
                }
                setJobMatches(data.job_matches || []);
            }
        } catch (err) {
            setRoadmapError((err as Error).message || 'Không thể tải lộ trình. Vui lòng thử lại.');
        } finally {
            setRoadmapLoading(false);
        }
    };

    const loadJobMatches = async () => {
        setJobsLoading(true);
        setJobsError('');
        try {
            const res = await getJobMatches({
                location: jobLocation.trim() || undefined,
                minSalary: jobMinSalary ? Number(jobMinSalary) : undefined,
            });
            setJobMatches(('data' in res ? res.data : res) ?? []);
        } catch (err) {
            setJobsError((err as Error).message || 'Không thể tải job phù hợp. Vui lòng thử lại.');
        } finally {
            setJobsLoading(false);
        }
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!targetRole.trim()) return;
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const skills = currentSkills.split(',').map(s => s.trim()).filter(Boolean);
            const res = await getCareerAdvice(targetRole.trim(), skills);
            setResult(res.data);
        } catch (err) {
            setError((err as Error).message || 'Không thể tải dữ liệu. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleSimulate = async (technology: string) => {
        const res = await simulateCareerMove(technology);
        return res.data;
    };

    return (
        <div className="career-page">
            <div className="career-hero">
                <h1 className="career-title">Lộ trình nghề nghiệp</h1>
                <p className="career-subtitle">
                    Phân tích khoảng cách kỹ năng và nhận lộ trình học tập cá nhân hoá
                </p>
            </div>

            {hasToken && (
                <TechRecommendationCards
                    recommendations={nextSkills}
                    loading={roadmapLoading}
                    title="Nên học tiếp"
                    highlightSkills={highlightSkills}
                    emptyMessage={
                        roadmapError ||
                        (hasTechnologies === false
                            ? 'Hãy thêm công nghệ quan tâm trong Hồ sơ cá nhân để nhận gợi ý lộ trình, job phù hợp và kỹ năng nên học tiếp.'
                            : null)
                    }
                />
            )}

            {hasToken && (
                <CareerSimulator
                    suggestions={nextSkills.map((s) => s.tech_name).filter(Boolean)}
                    onSimulate={handleSimulate}
                />
            )}

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
                {result && <CareerResultPanel result={result} />}
            </div>

            {hasToken && (
                <JobMatchesCard
                    jobMatches={jobMatches}
                    jobsLoading={jobsLoading}
                    jobsError={jobsError}
                    jobLocation={jobLocation}
                    onJobLocationChange={setJobLocation}
                    jobMinSalary={jobMinSalary}
                    onJobMinSalaryChange={setJobMinSalary}
                    onSearch={loadJobMatches}
                />
            )}
        </div>
    );
}
