import { useState } from 'react';
import type { MouseEvent } from 'react';
import Modal from '../common/Modal';
import './ImageLightbox.css';

interface ImageLightboxProps {
    images: string[];
    startIndex?: number;
    onClose: () => void;
}

/** Full-size viewer for a post's images, with prev/next when there's more than one. */
export default function ImageLightbox({ images, startIndex = 0, onClose }: ImageLightboxProps) {
    const [index, setIndex] = useState(startIndex);
    const hasMultiple = images.length > 1;

    const prev = (e: MouseEvent) => {
        e.stopPropagation();
        setIndex((i) => (i - 1 + images.length) % images.length);
    };
    const next = (e: MouseEvent) => {
        e.stopPropagation();
        setIndex((i) => (i + 1) % images.length);
    };

    return (
        <Modal onClose={onClose} width="min(90vw, 800px)">
            <div className="lightbox-body">
                {hasMultiple && (
                    <button type="button" className="lightbox-nav lightbox-prev" onClick={prev} aria-label="Ảnh trước">‹</button>
                )}
                <img src={images[index]} alt={`Ảnh ${index + 1}/${images.length}`} className="lightbox-image" />
                {hasMultiple && (
                    <button type="button" className="lightbox-nav lightbox-next" onClick={next} aria-label="Ảnh sau">›</button>
                )}
            </div>
            {hasMultiple && <div className="lightbox-counter">{index + 1} / {images.length}</div>}
        </Modal>
    );
}
