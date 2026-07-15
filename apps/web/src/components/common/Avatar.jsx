import './Avatar.css';

export default function Avatar({ user, size = 40 }) {
    return user?.avatar_url ? (
        <img
            src={user.avatar_url}
            alt={user.full_name || 'Avatar'}
            className="shared-avatar-img"
            style={{ width: size, height: size }}
        />
    ) : (
        <div className="shared-avatar-icon" style={{ width: size, height: size }}>
            <svg width={size * 0.55} height={size * 0.55} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
            </svg>
        </div>
    );
}
