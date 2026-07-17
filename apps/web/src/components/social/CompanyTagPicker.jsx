import { useEffect, useRef, useState } from 'react';
import { getCompanies } from '../../api/companyService';
import CompanyLogo from '../common/CompanyLogo';
import './CompanyTagPicker.css';

/** Composer control to tag a post with a company, searched from the existing /companies list. */
export default function CompanyTagPicker({ selected, onSelect, onClear }) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const containerRef = useRef(null);

    useEffect(() => {
        if (!open) return undefined;
        const handleClickOutside = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [open]);

    useEffect(() => {
        if (!open) return undefined;
        const timer = setTimeout(async () => {
            setLoading(true);
            try {
                const res = await getCompanies({ q: query, size: 20 });
                setResults(res?.data ?? []);
            } catch {
                setResults([]);
            } finally {
                setLoading(false);
            }
        }, 250);
        return () => clearTimeout(timer);
    }, [query, open]);

    if (selected) {
        return (
            <div className="company-tag-chip">
                <CompanyLogo name={selected.name} size={20} />
                <span className="company-tag-chip-name">{selected.name}</span>
                <button
                    type="button"
                    className="company-tag-chip-remove"
                    onClick={onClear}
                    aria-label="Bỏ gắn thẻ công ty"
                >
                    ✕
                </button>
            </div>
        );
    }

    return (
        <div className="company-tag-picker" ref={containerRef}>
            <button type="button" className="btn btn-ghost company-tag-trigger" onClick={() => setOpen((o) => !o)}>
                🏢 Gắn thẻ công ty
            </button>
            {open && (
                <div className="company-tag-dropdown">
                    <input
                        className="company-tag-search"
                        placeholder="Tìm công ty..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        autoFocus
                    />
                    {loading ? (
                        <div className="company-tag-hint">Đang tìm...</div>
                    ) : results.length === 0 ? (
                        <div className="company-tag-hint">Không tìm thấy công ty nào.</div>
                    ) : (
                        <div className="company-tag-results">
                            {results.map((c) => (
                                <button
                                    type="button"
                                    key={c.id}
                                    className="company-tag-result-row"
                                    onClick={() => {
                                        onSelect(c);
                                        setOpen(false);
                                        setQuery('');
                                    }}
                                >
                                    <CompanyLogo name={c.name} size={24} />
                                    <span className="company-tag-result-name">{c.name}</span>
                                    {c.location && <span className="company-tag-result-location">{c.location}</span>}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
