import { apiClient } from '../utils/apiClient';

// GET /clustering/clusters
export const getClusters = async (isCoherent = true) => {
    const params = new URLSearchParams();
    if (isCoherent !== null) params.append('is_coherent', isCoherent.toString());
    return await apiClient(`/clustering/clusters?${params.toString()}`, { method: 'GET' });
};

// GET /clustering/clusters/{id}
export const getClusterById = async (id) => {
    return await apiClient(`/clustering/clusters/${id}`, { method: 'GET' });
};

// POST /clustering/predict/batch
export const batchPredictClusters = async (techNames) => {
    return await apiClient('/clustering/predict/batch', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ tech_names: techNames })
    });
};

// GET /clustering/tech/{name}/cluster
export const getClusterByTech = async (techName) => {
    return await apiClient(`/clustering/tech/${encodeURIComponent(techName)}/cluster`, { method: 'GET' });
};
