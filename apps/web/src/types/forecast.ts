// Domain types cho ForecastPanel (dự báo xu hướng công nghệ).
export interface ForecastSignal {
    signal: string;
    value: number | string;
    weight?: number;
}

export interface ForecastTrendPoint {
    month: string;
    job_count?: number;
    growth_rate?: number;
}

export interface Forecast {
    predicted_direction: 'growing' | 'declining' | 'stable' | string;
    confidence?: number;
    reasoning?: string;
    trend_data?: ForecastTrendPoint[];
    signals?: ForecastSignal[];
}
