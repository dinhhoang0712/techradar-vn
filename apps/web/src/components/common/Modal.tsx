import { useEffect } from 'react';
import type { ReactNode, MouseEvent } from 'react';
import './Modal.css';

interface ModalProps {
    title?: string;
    children: ReactNode;
    onClose: () => void;
    width?: string;
}

export default function Modal({ title, children, onClose, width }: ModalProps) {
    useEffect(() => {
        const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [onClose]);

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className="modal-content"
                style={width ? { maxWidth: width } : undefined}
                onClick={(e: MouseEvent) => e.stopPropagation()}
            >
                {title && <h3 className="modal-title">{title}</h3>}
                {children}
            </div>
        </div>
    );
}
