import { useState, useMemo } from 'react';
import Select from 'react-select';
import CompanyLogo from '../common/CompanyLogo';
import TechRadarChart from './TechRadarChart';
import { CHART_PALETTE } from '../../utils/chartPalette';

const MAX_COMPARE = 4;

const selectStyles = {
    control: (b) => ({ ...b, background: 'var(--surface-2)', borderColor: 'var(--border)', color: 'var(--text)', minHeight: 38, boxShadow: 'none', '&:hover': { borderColor: 'var(--primary)' } }),
    menu: (b) => ({ ...b, background: 'var(--surface-2)', border: '1px solid var(--border)', zIndex: 200 }),
    option: (b, s) => ({ ...b, background: s.isFocused ? 'var(--surface)' : 'transparent', color: 'var(--text)', cursor: 'pointer' }),
    multiValue: (b) => ({ ...b, background: 'var(--primary-glow)', borderRadius: 4 }),
    multiValueLabel: (b) => ({ ...b, color: 'var(--primary-light)' }),
    multiValueRemove: (b) => ({ ...b, color: 'var(--primary-light)', '&:hover': { background: 'var(--primary)', color: '#fff' } }),
    input: (b) => ({ ...b, color: 'var(--text)' }),
    placeholder: (b) => ({ ...b, color: 'var(--text-3)' }),
};

function jaccard(a = [], b = []) {
    const setA = new Set(a.map(t => t.toLowerCase()));
    const setB = new Set(b.map(t => t.toLowerCase()));
    const intersection = [...setA].filter(t => setB.has(t)).length;
    const union = new Set([...setA, ...setB]).size;
    return union === 0 ? 0 : intersection / union;
}

// Panel so sánh tối đa 4 công ty cùng lúc: overlay radar "Tech DNA" + bảng số liệu.
export default function CompareCompaniesPanel({ companies, initialSelected = [], onClose }) {
    const [selected, setSelected] = useState(initialSelected);

    const options = useMemo(() => companies.map(c => ({ value: c.id, label: c.name, company: c })), [companies]);
    const value = useMemo(() => selected.map(c => ({ value: c.id, label: c.name, company: c })), [selected]);
    const series = useMemo(() => selected.map((c, i) => ({
        name: c.name, techStack: c.tech_stack, color: CHART_PALETTE[i % CHART_PALETTE.length],
    })), [selected]);

    const handleChange = (opts) => {
        if (opts.length > MAX_COMPARE) return;
        setSelected(opts.map(o => o.company));
    };

    return (
        <div className="compare-companies-overlay" onClick={onClose}>
            <div className="compare-companies-modal card" onClick={e => e.stopPropagation()}>
                <div className="compare-companies-header">
                    <h2 className="section-title">So sánh công ty</h2>
                    <button className="detail-close" onClick={onClose} aria-label="Đóng">✕</button>
                </div>

                <Select
                    isMulti
                    options={options}
                    value={value}
                    onChange={handleChange}
                    styles={selectStyles}
                    placeholder={`Chọn tối đa ${MAX_COMPARE} công ty để so sánh...`}
                />

                {selected.length === 0 && (
                    <p className="company-empty-hint" style={{ marginTop: 16 }}>
                        Chọn ít nhất 1 công ty ở ô trên để bắt đầu so sánh.
                    </p>
                )}

                {selected.length > 0 && (
                    <>
                        <div className="compare-companies-radar">
                            <TechRadarChart series={series} height={320} />
                        </div>

                        <div className="compare-companies-table-wrap">
                            <table className="compare-companies-table">
                                <thead>
                                    <tr>
                                        <th>Công ty</th>
                                        <th>Địa điểm</th>
                                        <th>Số tin tuyển dụng</th>
                                        {selected.length > 1 && <th>% trùng tech với {selected[0].name}</th>}
                                    </tr>
                                </thead>
                                <tbody>
                                    {selected.map((c, i) => (
                                        <tr key={c.id}>
                                            <td>
                                                <div className="compare-companies-identity">
                                                    <CompanyLogo name={c.name} size={28} />
                                                    <span style={{ color: series[i].color }}>{c.name}</span>
                                                </div>
                                            </td>
                                            <td>{c.location || 'Chưa rõ'}</td>
                                            <td>{c.job_count}</td>
                                            {selected.length > 1 && (
                                                <td>{i === 0 ? '—' : `${Math.round(jaccard(c.tech_stack, selected[0].tech_stack) * 100)}%`}</td>
                                            )}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
