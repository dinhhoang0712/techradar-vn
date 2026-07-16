export default function RingGauge({ percent = 0, size = 40, strokeWidth = 4, label, className = '' }) {
    const clamped = Math.min(Math.max(percent, 0), 100);
    const radius = (size - strokeWidth) / 2;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference * (1 - clamped / 100);
    const gradientId = `ring-gauge-gradient-${size}-${strokeWidth}`;

    return (
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className={`ring-gauge ${className}`}>
            <defs>
                <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="var(--primary)" />
                    <stop offset="100%" stopColor="var(--accent)" />
                </linearGradient>
            </defs>
            <circle
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke="var(--border)"
                strokeWidth={strokeWidth}
            />
            <circle
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={`url(#${gradientId})`}
                strokeWidth={strokeWidth}
                strokeDasharray={circumference}
                strokeDashoffset={offset}
                strokeLinecap="round"
                transform={`rotate(-90 ${size / 2} ${size / 2})`}
                style={{ transition: 'stroke-dashoffset 0.6s ease' }}
            />
            {label !== undefined && label !== null && (
                <text
                    x="50%"
                    y="50%"
                    dominantBaseline="middle"
                    textAnchor="middle"
                    fill="var(--text)"
                    fontSize={size * 0.28}
                    fontWeight="700"
                >
                    {label}
                </text>
            )}
        </svg>
    );
}
