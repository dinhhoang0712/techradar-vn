const HUES = [214, 258, 168, 28, 340, 190];

function hashHue(name) {
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
    }
    return HUES[hash % HUES.length];
}

export function getInitials(name) {
    const words = name.trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return '?';
    if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

export function hashGradient(name) {
    const hue = hashHue(name || '?');
    return `linear-gradient(135deg, hsl(${hue}, 85%, 62%) 0%, hsl(${(hue + 55) % 360}, 85%, 62%) 100%)`;
}
