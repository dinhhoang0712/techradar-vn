import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { RawGraphNode, RawGraphLink } from '../utils/graphNormalize';

export interface GraphExploreData {
    nodes: RawGraphNode[];
    edges?: RawGraphLink[];
    links?: RawGraphLink[];
}

export interface GraphRoadAnalysisData {
    found: boolean;
    nodes: RawGraphNode[];
    edges: RawGraphLink[];
}

// GET /graph/explore — keywords: array[string], depth: integer (default 1)
export const exploreGraph = async (
    keywords: string[] = [],
    depth = 1,
    location = '',
    minSalary: number | string | null = null,
): Promise<ApiResponse<GraphExploreData>> => {
    const params = new URLSearchParams();
    keywords.forEach(kw => params.append('keywords', kw));
    params.append('depth', String(depth));
    if (location) params.append('location', location);
    if (minSalary !== null && minSalary !== '') params.append('min_salary', minSalary.toString());
    return await apiClient(`/graph/explore?${params.toString()}`, { method: 'GET' });
};

// GET /graph/road_analysis — from: string, to: string
export const analyzeRoad = async (from: string, to: string): Promise<ApiResponse<GraphRoadAnalysisData>> => {
    const params = new URLSearchParams();
    params.append('from', from);
    params.append('to', to);
    return await apiClient(`/graph/road_analysis?${params.toString()}`, { method: 'GET' });
};

interface FilterGraphOptions {
    locations?: string[];
    nodeTypes?: string[];
    minSalary?: number | string | null;
    maxSalary?: number | string | null;
    sentiment?: string | null;
}

// POST /graph/filter — browse the whole graph by facets, no seed keyword required.
// Returns a flat node list (no edges). minSalary/maxSalary only affect nodes that carry
// a `salary` property (Job nodes); sentiment is "positive" | "negative" | "neutral".
export const filterGraph = async ({ locations, nodeTypes, minSalary, maxSalary, sentiment }: FilterGraphOptions = {}): Promise<ApiResponse<RawGraphNode[]>> => {
    return await apiClient('/graph/filter', {
        method: 'POST',
        body: JSON.stringify({
            locations: locations?.length ? locations : null,
            node_types: nodeTypes?.length ? nodeTypes : null,
            min_salary: minSalary === '' || minSalary == null ? null : Number(minSalary),
            max_salary: maxSalary === '' || maxSalary == null ? null : Number(maxSalary),
            sentiment: sentiment || null,
        }),
    });
};
