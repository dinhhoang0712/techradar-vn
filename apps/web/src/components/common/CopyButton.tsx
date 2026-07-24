import { useState, useCallback, useRef, useEffect } from 'react';

interface CopyButtonProps {
    text: string;
    label?: string;
    className?: string;
}

// Nút copy dùng chung cho nội dung AI sinh ra (chat bubble, báo cáo...) — copy plain text (không
// kèm cú pháp markdown thô) vào clipboard, hiện trạng thái "Đã copy" 1.5s rồi tự trở lại.
export default function CopyButton({ text, label = 'Copy', className = '' }: CopyButtonProps) {
    const [copied, setCopied] = useState(false);
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

    const handleCopy = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(text);
        } catch {
            return;
        }
        setCopied(true);
        if (timerRef.current) clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => setCopied(false), 1500);
    }, [text]);

    return (
        <button
            type="button"
            className={`copy-content-btn${copied ? ' copied' : ''} ${className}`.trim()}
            onClick={handleCopy}
            title="Sao chép nội dung"
        >
            {copied ? (
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
            ) : (
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2"></rect>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                </svg>
            )}
            {copied ? 'Đã copy' : label}
        </button>
    );
}
