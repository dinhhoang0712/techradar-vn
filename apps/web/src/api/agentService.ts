import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';

export interface AgentStep {
    tool: string;
    input: string;
    output?: string;
}

export interface AgentAnswer {
    answer: string;
    steps: AgentStep[];
}

// POST /agent — body: { query }. user_id is attached server-side from the JWT.
export const runAgent = async (query: string): Promise<ApiResponse<AgentAnswer>> => {
    return await apiClient('/agent', {
        method: 'POST',
        body: JSON.stringify({ query }),
    });
};
