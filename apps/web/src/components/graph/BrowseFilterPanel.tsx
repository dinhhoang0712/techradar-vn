export interface BrowseFilters {
    locations: string[];
    nodeTypes: string[];
    sentiment: string;
    minSalary: string;
    maxSalary: string;
}

interface BrowseFilterPanelProps {
    filters: BrowseFilters;
    onToggleLocation: (loc: string) => void;
    onToggleNodeType: (nodeType: string) => void;
    onSentimentChange: (value: string) => void;
    onMinSalaryChange: (value: string) => void;
    onMaxSalaryChange: (value: string) => void;
    onSearch: () => void;
    loading: boolean;
}

const LOCATIONS = ['Hồ Chí Minh', 'Hà Nội', 'Đà Nẵng'];
const NODE_TYPE_OPTIONS = ['Technology', 'Company', 'Job', 'Skill', 'Article'];
const SENTIMENT_OPTIONS: [string, string][] = [['', 'Tất cả'], ['positive', 'Tích cực'], ['negative', 'Tiêu cực'], ['neutral', 'Trung lập']];

// Bộ lọc của tab "Duyệt bộ lọc" — /graph/filter không cần từ khóa gốc, chỉ cần location/loại
// node/cảm xúc/khoảng lương.
export default function BrowseFilterPanel({ filters, onToggleLocation, onToggleNodeType, onSentimentChange, onMinSalaryChange, onMaxSalaryChange, onSearch, loading }: BrowseFilterPanelProps) {
    return (
        <div className="filter-panel card">
            <h3 className="filter-title">Bộ lọc</h3>
            <div className="filter-group">
                <label className="filter-label">Địa điểm</label>
                <div className="pill-group">
                    {LOCATIONS.map(loc => (
                        <button
                            type="button" key={loc}
                            className={`chip${filters.locations.includes(loc) ? ' active' : ''}`}
                            onClick={() => onToggleLocation(loc)}
                        >
                            {loc}
                        </button>
                    ))}
                </div>
            </div>
            <div className="filter-group">
                <label className="filter-label">Loại node</label>
                <div className="pill-group">
                    {NODE_TYPE_OPTIONS.map(nt => (
                        <button
                            type="button" key={nt}
                            className={`chip${filters.nodeTypes.includes(nt) ? ' active' : ''}`}
                            onClick={() => onToggleNodeType(nt)}
                        >
                            {nt}
                        </button>
                    ))}
                </div>
            </div>
            <div className="filter-group">
                <label className="filter-label">Cảm xúc</label>
                <div className="pill-group">
                    {SENTIMENT_OPTIONS.map(([val, label]) => (
                        <button
                            type="button" key={val || 'all'}
                            className={`pill${filters.sentiment === val ? ' active' : ''}`}
                            onClick={() => onSentimentChange(val)}
                        >
                            {label}
                        </button>
                    ))}
                </div>
            </div>
            <div className="filter-group">
                <label className="filter-label">Mức lương (triệu/tháng)</label>
                <div style={{ display: 'flex', gap: 8 }}>
                    <input
                        className="search-input" type="number" placeholder="Từ"
                        value={filters.minSalary}
                        onChange={e => onMinSalaryChange(e.target.value)}
                    />
                    <input
                        className="search-input" type="number" placeholder="Đến"
                        value={filters.maxSalary}
                        onChange={e => onMaxSalaryChange(e.target.value)}
                    />
                </div>
                <span className="form-hint">Chỉ áp dụng cho node Job có lương ghi rõ số</span>
            </div>
            <button className="btn btn-primary w-full" onClick={onSearch} disabled={loading}>
                {loading ? 'Đang tìm...' : 'Áp dụng bộ lọc'}
            </button>
        </div>
    );
}
