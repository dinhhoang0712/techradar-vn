import type { JobMatch } from '../../types/career';
import { EXPERIENCE_LEVELS } from '../../constants/experienceLevels';

interface JobMatchesCardProps {
    jobMatches: JobMatch[] | null;
    jobsLoading: boolean;
    jobsError: string;
    jobLocation: string;
    onJobLocationChange: (value: string) => void;
    jobMinSalary: string;
    onJobMinSalaryChange: (value: string) => void;
    jobLevel: string;
    onJobLevelChange: (value: string) => void;
    onSearch: () => void;
}

// Danh sách job phù hợp với hồ sơ, xếp hạng theo % kỹ năng trùng với yêu cầu tin tuyển dụng —
// kèm bộ lọc địa điểm/lương tối thiểu và nút tìm lại.
export default function JobMatchesCard({
    jobMatches, jobsLoading, jobsError,
    jobLocation, onJobLocationChange,
    jobMinSalary, onJobMinSalaryChange,
    jobLevel, onJobLevelChange,
    onSearch,
}: JobMatchesCardProps) {
    return (
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
                    onClick={onSearch}
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
                    onChange={e => onJobLocationChange(e.target.value)}
                    placeholder="Địa điểm (VD: Hà Nội)"
                />
                <input
                    type="number"
                    className="form-input"
                    value={jobMinSalary}
                    onChange={e => onJobMinSalaryChange(e.target.value)}
                    placeholder="Lương tối thiểu (triệu VND)"
                    min="0"
                />
                <select
                    className="form-input"
                    value={jobLevel}
                    onChange={e => onJobLevelChange(e.target.value)}
                >
                    <option value="">Tất cả cấp độ</option>
                    {EXPERIENCE_LEVELS.map(l => (
                        <option key={l} value={l}>{l}</option>
                    ))}
                </select>
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
                                    {job.level && <span className="job-match-level">{job.level}</span>}
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
    );
}
