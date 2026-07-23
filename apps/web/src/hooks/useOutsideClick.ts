import { useEffect } from 'react';
import type { RefObject } from 'react';

/**
 * Đóng dropdown/menu khi người dùng click ra ngoài `ref`.
 * Dùng chung cho các menu kiểu dropdown (avatar menu, tools menu, notification bell, ...)
 * để logic mousedown-listener + cleanup chỉ cần viết một chỗ.
 *
 * @param ref - Phần tử bao ngoài dropdown (click bên trong ref sẽ không đóng).
 * @param onOutside - Gọi khi click xảy ra bên ngoài ref.
 * @param enabled - Cho phép tắt listener khi dropdown đang đóng.
 */
export function useOutsideClick(
    ref: RefObject<HTMLElement | null>,
    onOutside: (e: MouseEvent) => void,
    enabled = true,
): void {
    useEffect(() => {
        if (!enabled) return undefined;
        function handleClick(e: MouseEvent) {
            if (ref.current && !ref.current.contains(e.target as Node)) {
                onOutside(e);
            }
        }
        document.addEventListener('mousedown', handleClick);
        return () => document.removeEventListener('mousedown', handleClick);
    }, [ref, onOutside, enabled]);
}
