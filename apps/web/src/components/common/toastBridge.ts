// Cầu nối để gọi toast từ ngoài cây React (vd: apiClient.js — module thuần,
// không thể dùng hook useToast()). ToastProvider tự đăng ký handler khi mount.
export interface ToastOptions {
    title?: string;
    body?: string;
    variant?: 'info' | 'success' | 'error';
    duration?: number;
    onClick?: () => void;
}

let handler: ((options: ToastOptions) => void) | null = null;

export function registerToastHandler(fn: ((options: ToastOptions) => void) | null): void {
    handler = fn;
}

export function showGlobalToast(options: ToastOptions): void {
    handler?.(options);
}
