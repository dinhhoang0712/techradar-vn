import { createContext, useCallback, useContext, useRef, useState } from 'react';
import './ToastProvider.css';

const ToastContext = createContext(null);

let nextId = 1;

export function ToastProvider({ children }) {
    const [toasts, setToasts] = useState([]);
    const timers = useRef(new Map());

    const dismiss = useCallback((id) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
        const timer = timers.current.get(id);
        if (timer) {
            clearTimeout(timer);
            timers.current.delete(id);
        }
    }, []);

    const showToast = useCallback(({ title, body, variant = 'info', duration = 5000, onClick } = {}) => {
        const id = nextId++;
        setToasts((prev) => [...prev, { id, title, body, variant, onClick }]);
        if (duration > 0) {
            const timer = setTimeout(() => dismiss(id), duration);
            timers.current.set(id, timer);
        }
        return id;
    }, [dismiss]);

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

export function useToast() {
    const showToast = useContext(ToastContext);
    if (!showToast) throw new Error('useToast must be used within a ToastProvider');
    return showToast;
}
