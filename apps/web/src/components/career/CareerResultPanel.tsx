import { renderMarkdown } from '../../utils/markdown';
import type { CareerAdvice } from '../../types/career';

// Kết quả phân tích lộ trình nghề nghiệp: vai trò mục tiêu, kỹ năng hiện có, khoảng cách kỹ năng
// cần học, và roadmap markdown do backend sinh ra.
export default function CareerResultPanel({ result }: { result: CareerAdvice }) {
    return (
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

                {result.current_skills && result.current_skills.length > 0 && (
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

            {result.skill_gap && result.skill_gap.length > 0 && (
                <div className="card">
                    <h3 className="section-title">Kỹ năng cần học</h3>
                    <div className="skill-gap-timeline">
                        {result.skill_gap.map((step, idx) => (
                            <div key={step.skill} className="skill-gap-timeline-item">
                                <div className="skill-gap-timeline-rail">
                                    <div className="skill-gap-marker">{step.priority}</div>
                                    {result.skill_gap && idx < result.skill_gap.length - 1 && <div className="skill-gap-connector" />}
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
    );
}
