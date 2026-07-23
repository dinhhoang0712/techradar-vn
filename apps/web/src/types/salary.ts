// Domain types cho SalaryPage/SalaryDetailPanel.
export interface SalaryTech {
    tech_name: string;
    median_salary_mvnd: number;
    avg_salary_mvnd?: number;
    p25_salary_mvnd?: number;
    p75_salary_mvnd?: number;
    min_salary_mvnd?: number;
    max_salary_mvnd?: number;
    total_jobs: number;
    jobs_with_salary: number;
    salary_range?: string;
    top_co_techs?: string[];
}
