import { getInitials, hashGradient } from '../../utils/avatarColor';
import './Avatar.css';

interface AvatarUser {
    full_name?: string;
    avatar_url?: string | null;
}

interface AvatarProps {
    user?: AvatarUser | null;
    size?: number;
    ring?: boolean;
}

export default function Avatar({ user, size = 40, ring = false }: AvatarProps) {
    const name = user?.full_name || '';
    const ringClass = ring ? ' gradient-ring active' : '';

    if (user?.avatar_url) {
        return (
            <img
                src={user.avatar_url}
                alt={name || 'Avatar'}
                className={`shared-avatar-img${ringClass}`}
                style={{ width: size, height: size }}
            />
        );
    }

    if (name) {
        return (
            <div
                className={`shared-avatar-initials${ringClass}`}
                style={{ width: size, height: size, fontSize: size * 0.36, background: hashGradient(name) }}
            >
                {getInitials(name)}
            </div>
        );
    }

    return (
        <div className={`shared-avatar-icon${ringClass}`} style={{ width: size, height: size }}>
            <svg width={size * 0.55} height={size * 0.55} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
            </svg>
        </div>
    );
}
