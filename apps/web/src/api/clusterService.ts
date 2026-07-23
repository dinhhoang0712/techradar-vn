import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { ClusterSummary, ClusterDetail, ClusterByTechResult } from '../types/cluster';

// GET /clustering/clusters
export const getClusters = async (isCoherent: boolean | null = true): Promise<ApiResponse<ClusterSummary[]> | ClusterSummary[]> => {
    const params = new URLSearchParams();
    if (isCoherent !== null) params.append('is_coherent', isCoherent.toString());
    return await apiClient(`/clustering/clusters?${params.toString()}`, { method: 'GET' });
};

// GET /clustering/clusters/{id}
export const getClusterById = async (id: number | string): Promise<ApiResponse<ClusterDetail> | ClusterDetail> => {
    return await apiClient(`/clustering/clusters/${id}`, { method: 'GET' });
};

// GET /clustering/tech/{name}/cluster
export const getClusterByTech = async (techName: string): Promise<ApiResponse<ClusterByTechResult> | ClusterByTechResult> => {
    return await apiClient(`/clustering/tech/${encodeURIComponent(techName)}/cluster`, { method: 'GET' });
};
