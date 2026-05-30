import './Topbar.css';

export default function Topbar({ title }) {
    return (
        <header className="topbar">
            <div className="topbar-left">
                <h1 className="topbar-title">{title}</h1>
            </div>
            <div className="topbar-right">
                <div className="topbar-avatar">TT</div>
            </div>
        </header>
    );
}
