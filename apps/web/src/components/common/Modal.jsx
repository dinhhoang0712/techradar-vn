import { useEffect } from 'react';
import './Modal.css';

export default function Modal({ title, children, onClose, width }) {
    useEffect(() => {
        const onKey = (e) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [onClose]);

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className="modal-content"
                style={width ? { maxWidth: width } : undefined}
                onClick={(e) => e.stopPropagation()}
            >
                {title && <h3 className="modal-title">{title}</h3>}
                {children}
            </div>
        </div>
    );
}
