// Lỗi chuẩn hoá cho mọi call qua apiClient — thay cho pattern cũ "new Error(msg); err.status = n"
// (mutate sau khi tạo, TS không track được field .status trên Error thường).
export class ApiError extends Error {
    status: number;

    constructor(message: string, status: number) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
    }
}

// Hình dạng envelope phổ biến nhất trả về từ backend: { data: T }. Một số endpoint (VD
// fetchAdminDashboardStats) trả thêm `status`, một số khác (VD streamFeed's SSE payload) không theo
// hình dạng này — những chỗ đó tự khai báo type riêng thay vì ép vào đây.
export interface ApiResponse<T> {
    data: T;
    status?: string;
    message?: string;
}
