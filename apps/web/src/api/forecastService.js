import { apiClient } from '../utils/apiClient';

// GET /forecast?technology=X&horizonMonths=6 — PUBLIC (không cần JWT)
export const getForecast = async (technology, horizonMonths = 6) => {
    const params = new URLSearchParams({ technology, horizonMonths });
    return await apiClient(`/forecast?${params.toString()}`, { method: 'GET' });
};
