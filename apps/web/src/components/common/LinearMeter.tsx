import type { CSSProperties } from 'react';

interface LinearMeterProps {
    percent?: number;
    variant?: 'fill' | 'marker';
    trackHeight?: number;
    className?: string;
    style?: CSSProperties;
}

/**
 * Linear counterpart to RingGauge — a neutral track with either a percent-filled bar
 * (e.g. confidence) or a single position marker (e.g. where a value sits inside a range).
 * Centralizes the track/fill/marker visuals and transition so every percent meter in the
 * app looks and animates the same way, instead of each spot hand-rolling its own
 * track div + fill div.
 */
export default function LinearMeter({ percent = 0, variant = 'fill', trackHeight = 4, className = '', style }: LinearMeterProps) {
    const clamped = Math.min(Math.max(percent, 0), 100);

    return (
        <div className={`linear-meter linear-meter--${variant} ${className}`} style={style}>
            <div
                className="linear-meter-track"
                style={{
                    position: 'relative',
                    height: trackHeight,
                    borderRadius: trackHeight / 2,
                    background: 'var(--border)',
                    overflow: variant === 'marker' ? 'visible' : 'hidden',
                }}
            >
                {variant === 'marker' ? (
                    <div
                        className="linear-meter-marker"
                        style={{
                            position: 'absolute',
                            top: '50%',
                            left: `${clamped}%`,
                            width: 10,
                            height: 10,
                            borderRadius: '50%',
                            background: 'var(--primary)',
                            boxShadow: '0 0 0 2px var(--surface-2)',
                            transform: 'translate(-50%, -50%)',
                            transition: 'left 0.6s ease',
                        }}
                    />
                ) : (
                    <div
                        className="linear-meter-fill"
                        style={{
                            height: '100%',
                            width: `${clamped}%`,
                            borderRadius: trackHeight / 2,
                            background: 'var(--primary)',
                            transition: 'width 0.6s ease',
                        }}
                    />
                )}
            </div>
        </div>
    );
}
