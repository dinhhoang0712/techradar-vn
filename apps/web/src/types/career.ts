// Domain types cho CareerPage: phân tích lộ trình nghề nghiệp, roadmap tự động, mô phỏng học công nghệ mới.
export interface SkillGapStep {
    skill: string;
    reason: string;
    priority: number;
    job_demand?: number | null;
}

export interface CareerAdvice {
    target_role: string;
    estimated_months?: number;
    current_skills?: string[];
    current_level?: string;
    target_level?: string;
    skill_gap?: SkillGapStep[];
    roadmap?: string;
}

export interface NextSkill {
    tech_name: string;
    ring?: string;
    reason?: string;
    growth_rate?: number;
    co_occurrence?: number;
    confidence?: number;
    job_matches_needing_it?: number;
    tech_path?: string[];
    [key: string]: unknown;
}

export interface JobMatch {
    title: string;
    company?: string;
    location?: string;
    score: number;
    source_url?: string;
    salary_min_mvnd?: number;
    salary_max_mvnd?: number;
    salary_raw?: string;
    level?: string;
    matched_skills?: string[];
    missing_skills?: string[];
}

export interface CareerRoadmap {
    has_technologies: boolean;
    current_technologies?: string[];
    next_skills?: NextSkill[];
    career_path?: CareerAdvice;
    job_matches?: JobMatch[];
}

export interface CareerSimulationSalary {
    median_salary_mvnd: number;
    p25_salary_mvnd: number;
    p75_salary_mvnd: number;
}

export interface CareerSimulationForecast {
    predicted_direction: 'growing' | 'declining' | 'stable' | string;
    confidence?: number;
    reasoning?: string;
}

export interface CareerSimulationResult {
    technology: string;
    current_job_matches: number;
    simulated_job_matches: number;
    salary?: CareerSimulationSalary | null;
    forecast?: CareerSimulationForecast | null;
}

export interface LevelMoveSimulationResult {
    current_level: string | null;
    target_level: string;
    current_job_matches: number;
    simulated_job_matches: number;
    salary?: CareerSimulationSalary | null;
}
