import { useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import './PasswordInput.css';

function EyeIcon() {
    return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
        </svg>
    );
}

function EyeOffIcon() {
    return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19M14.12 14.12a3 3 0 1 1-4.24-4.24" />
            <line x1="1" y1="1" x2="23" y2="23" />
        </svg>
    );
}

type PasswordInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>;

/**
 * Drop-in replacement for <input type="password">, adding a show/hide toggle.
 * All props (value, onChange, placeholder, required, minLength, className, id, ...)
 * are forwarded to the underlying <input>.
 */
export default function PasswordInput({ style, ...props }: PasswordInputProps) {
    const [visible, setVisible] = useState(false);

    return (
        <div className="password-input-wrapper">
            <input
                {...props}
                type={visible ? 'text' : 'password'}
                style={{ paddingRight: 42, ...style }}
            />
            <button
                type="button"
                className="password-toggle-btn"
                onClick={() => setVisible(v => !v)}
                tabIndex={-1}
                aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
                {visible ? <EyeOffIcon /> : <EyeIcon />}
            </button>
        </div>
    );
}
