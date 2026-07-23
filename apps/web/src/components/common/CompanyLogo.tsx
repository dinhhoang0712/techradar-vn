import { getInitials, hashGradient } from '../../utils/avatarColor';
import './CompanyLogo.css';

interface CompanyLogoProps {
    name?: string;
    logoUrl?: string;
    size?: number;
    className?: string;
}

export default function CompanyLogo({ name = '', logoUrl, size = 48, className = '' }: CompanyLogoProps) {
    if (logoUrl) {
        return (
            <img
                src={logoUrl}
                alt={name}
                className={`company-logo-img ${className}`}
                style={{ width: size, height: size }}
            />
        );
    }

    return (
        <div
            className={`company-logo-fallback ${className}`}
            style={{
                width: size,
                height: size,
                fontSize: size * 0.36,
                background: hashGradient(name),
            }}
        >
            {getInitials(name)}
        </div>
    );
}
