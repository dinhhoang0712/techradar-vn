// Cầu nối để gọi toast từ ngoài cây React (vd: apiClient.js — module thuần,
// không thể dùng hook useToast()). ToastProvider tự đăng ký handler khi mount.
let handler = null;

export function registerToastHandler(fn) {
    handler = fn;
}

export function showGlobalToast(options) {
    handler?.(options);
}
