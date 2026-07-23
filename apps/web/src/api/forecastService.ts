import { apiClient } from '../utils/apiClient';
import type { ApiResponse } from '../types/api';
import type { Forecast } from '../types/forecast';

// GET /forecast?technology=X&horizonMonths=6 — PUBLIC (không cần JWT)
export const getForecast = async (technology: string, horizonMonths = 6): Promise<ApiResponse<Forecast> | Forecast> => {
    const params = new URLSearchParams({ technology, horizonMonths: String(horizonMonths) });
    return await apiClient(`/forecast?${params.toString()}`, { method: 'GET' });
};
