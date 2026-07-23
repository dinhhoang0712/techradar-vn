// Shared absolute-time formatter (vi-VN locale) — used by admin/notification pages so a
// format change only needs to happen in one place instead of in every page's own copy.
export function formatDateTime(iso?: string | null, fallback = ''): string {
    if (!iso) return fallback;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return fallback;
    return d.toLocaleString('vi-VN');
}
