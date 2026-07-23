import { createContext, useContext } from 'react';
import type { ToastOptions } from './toastBridge';

export type ShowToastFn = (options: ToastOptions) => number;

export const ToastContext = createContext<ShowToastFn | null>(null);

export function useToast(): ShowToastFn {
    const showToast = useContext(ToastContext);
    if (!showToast) throw new Error('useToast must be used within a ToastProvider');
    return showToast;
}
