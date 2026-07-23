import { logoutUser } from '../api/authService';

/**
 * Luồng đăng xuất dùng chung: báo server (best-effort), rồi luôn luôn xoá
 * token phía client và điều hướng về /login dù server call thành công hay
 * thất bại. Tách ra đây để các nơi có nút "Đăng xuất" (header, admin sidebar, ...)
 * không phải chép lại cùng một khối try/catch/finally.
 *
 * @param navigate - Hàm điều hướng (thường là useNavigate()).
 */
export async function performLogout(navigate: (path: string) => void): Promise<void> {
    try {
        await logoutUser();
    } catch (err) {
        console.warn('[auth] Logout API failed:', err);
    } finally {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        navigate('/login');
    }
}
