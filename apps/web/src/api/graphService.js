import { apiClient } from '../utils/apiClient';

// GET /graph/explore — keywords: array[string], depth: integer (default 1)
export const exploreGraph = async (keywords = [], depth = 1, location = '', minSalary = null) => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('depth', depth);
    if (location) params.append('location', location);
    if (minSalary !== null && minSalary !== '') params.append('min_salary', minSalary.toString());
    return await apiClient(`/graph/explore?${params.toString()}`, { method: 'GET' });
};

// GET /graph/road_analysis — from: string, to: string
export const analyzeRoad = async (from, to) => {
    const params = new URLSearchParams();
    params.append('from', from);
    params.append('to', to);
    return await apiClient(`/graph/road_analysis?${params.toString()}`, { method: 'GET' });
};

// POST /graph/filter — browse the whole graph by facets, no seed keyword required.
// Returns a flat node list (no edges). minSalary/maxSalary only affect nodes that carry
// a `salary` property (Job nodes); sentiment is "positive" | "negative" | "neutral".
export const filterGraph = async ({ locations, nodeTypes, minSalary, maxSalary, sentiment } = {}) => {
    return await apiClient('/graph/filter', {
        method: 'POST',
        body: JSON.stringify({
            locations: locations?.length ? locations : null,
            nodeTypes: nodeTypes?.length ? nodeTypes : null,
            minSalary: minSalary === '' || minSalary == null ? null : Number(minSalary),
            maxSalary: maxSalary === '' || maxSalary == null ? null : Number(maxSalary),
            sentiment: sentiment || null,
        }),
    });
};
