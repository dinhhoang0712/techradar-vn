import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ToastContext } from './toastContext';
import { registerToastHandler } from './toastBridge';
import type { ToastOptions } from './toastBridge';
import './ToastProvider.css';

interface Toast {
    id: number;
    title?: string;
    body?: string;
    variant: 'info' | 'success' | 'error';
    onClick?: () => void;
}

let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

    const dismiss = useCallback((id: number) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
        const timer = timers.current.get(id);
        if (timer) {
            clearTimeout(timer);
            timers.current.delete(id);
        }
    }, []);

    const showToast = useCallback(({ title, body, variant = 'info', duration = 5000, onClick }: ToastOptions = {}) => {
        const id = nextId++;
        setToasts((prev) => [...prev, { id, title, body, variant, onClick }]);
        if (duration > 0) {
            const timer = setTimeout(() => dismiss(id), duration);
            timers.current.set(id, timer);
        }
        return id;
    }, [dismiss]);

    // Cho phép code ngoài React (apiClient.js) hiện toast qua toastBridge.
    useEffect(() => {
        registerToastHandler(showToast);
        return () => registerToastHandler(null);
    }, [showToast]);

    return (
        <ToastContext.Provider value={showToast}>
            {children}
            <div className="toast-stack">
                {toasts.map((t) => (
                    <div
                        key={t.id}
                        className={`toast toast-${t.variant}${t.onClick ? ' clickable' : ''}`}
                        onClick={() => { t.onClick?.(); dismiss(t.id); }}
                    >
                        <div className="toast-body">
                            {t.title && <div className="toast-title">{t.title}</div>}
                            {t.body && <div className="toast-text">{t.body}</div>}
                        </div>
                        <button
                            type="button"
                            className="toast-close"
                            aria-label="Đóng"
                            onClick={(e) => { e.stopPropagation(); dismiss(t.id); }}
                        >
                            ✕
                        </button>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
}
