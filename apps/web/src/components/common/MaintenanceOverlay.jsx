import './MaintenanceOverlay.css';

// Full-viewport overlay (covers header/nav too) used to block a page while its
// backing service is disabled or unreachable. Escapes the routed page-content
// layout on purpose — a plain in-flow render would sit under the header.
export default function MaintenanceOverlay({ children }) {
    return <div className="maintenance-overlay">{children}</div>;
}
