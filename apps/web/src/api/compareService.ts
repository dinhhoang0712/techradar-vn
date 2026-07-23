import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { CompareItem, LlmSummaryResult } from '../types/trend';

// GET /compare/search — keywords: array[string], months: integer (default 6)
export const getCompareSearch = async (keywords: string[] = [], months = 6): Promise<ApiResponse<CompareItem[]>> => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('months', String(months));
    return await apiClient(`/compare/search?${params.toString()}`, { method: 'GET' });
};

interface LlmSummaryParams {
    technology1: string;
    technology2: string;
    growthRate1: number;
    growthRate2: number;
    jobCount1: number;
    jobCount2: number;
    articleCount1?: number;
    articleCount2?: number;
    comparisonScore?: number;
}

// POST /compare/llm-summary — body keys must match Jackson's SNAKE_CASE strategy
// for TechComparison (growthRate1 -> growth_rate1, not growth_rate_1).
export const getLlmSummary = async ({
    technology1, technology2,
    growthRate1, growthRate2,
    jobCount1, jobCount2,
    articleCount1 = 0, articleCount2 = 0,
    comparisonScore = 0,
}: LlmSummaryParams): Promise<ApiResponse<LlmSummaryResult>> => {
    return await apiClient('/compare/llm-summary', {
        method: 'POST',
        body: JSON.stringify({
            technology1, technology2,
            growth_rate1: growthRate1, growth_rate2: growthRate2,
            job_count1: jobCount1, job_count2: jobCount2,
            article_count1: articleCount1, article_count2: articleCount2,
            comparison_score: comparisonScore,
        }),
    });
};
