import { useRef, useState } from 'react';
import type { ChangeEvent, FocusEvent } from 'react';
import { searchUsers } from '../../api/socialService';
import type { PostAuthor } from '../../types/social';
import './MentionTextarea.css';

const MENTION_QUERY_RE = /@([\p{L}0-9_ ]{0,30})$/u;

interface MentionUser extends PostAuthor {
    id: string;
}

interface MentionTextareaProps {
    as?: 'textarea' | 'input';
    value: string;
    onChange: (value: string) => void;
    mentionedUserIds?: string[];
    onMentionedUserIdsChange?: (ids: string[]) => void;
    className?: string;
    placeholder?: string;
    rows?: number;
    maxLength?: number;
    disabled?: boolean;
    onFocus?: (e: FocusEvent) => void;
    onBlur?: () => void;
}

/**
 * A textarea/input with an @mention typeahead. Selecting a suggestion splices a display-only
 * "@FullName " string into the text and records the user's id in `mentionedUserIds` — the actual
 * mention notification is driven by that id list, not by parsing the text back out later (there's
 * no username field in this app to make an @id token durably resolvable).
 */
export default function MentionTextarea({
    as = 'textarea',
    value,
    onChange,
    mentionedUserIds = [],
    onMentionedUserIdsChange,
    className = '',
    placeholder,
    rows,
    maxLength,
    disabled,
    onFocus,
    onBlur,
}: MentionTextareaProps) {
    const [suggestions, setSuggestions] = useState<MentionUser[]>([]);
    const [showDropdown, setShowDropdown] = useState(false);
    const [loading, setLoading] = useState(false);
    const inputRef = useRef<HTMLTextAreaElement & HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    const handleChange = (e: ChangeEvent<HTMLTextAreaElement | HTMLInputElement>) => {
        const text = e.target.value;
        onChange(text);

        const cursorPos = e.target.selectionStart ?? text.length;
        const match = text.slice(0, cursorPos).match(MENTION_QUERY_RE);
        const query = match ? match[1] : null;

        if (query) {
            setShowDropdown(true);
            clearTimeout(debounceRef.current);
            debounceRef.current = setTimeout(async () => {
                setLoading(true);
                try {
                    const res = await searchUsers(query, 8);
                    setSuggestions((res?.data ?? []) as MentionUser[]);
                } catch {
                    setSuggestions([]);
                } finally {
                    setLoading(false);
                }
            }, 250);
        } else {
            setShowDropdown(false);
        }
    };

    const selectMention = (user: MentionUser) => {
        const el = inputRef.current;
        const cursorPos = el ? (el.selectionStart ?? value.length) : value.length;
        const before = value.slice(0, cursorPos);
        const after = value.slice(cursorPos);
        const match = before.match(MENTION_QUERY_RE);
        const start = match ? before.length - match[0].length : cursorPos;
        const newText = `${value.slice(0, start)}@${user.full_name} ${after}`;

        onChange(newText);
        onMentionedUserIdsChange?.([...new Set([...mentionedUserIds, user.id])]);
        setShowDropdown(false);
        setSuggestions([]);
        if (el) setTimeout(() => el.focus(), 0);
    };

    const Tag = as;

    return (
        <div className="mention-input-wrap">
            <Tag
                ref={inputRef}
                className={className}
                value={value}
                onChange={handleChange}
                placeholder={placeholder}
                rows={as === 'textarea' ? rows : undefined}
                maxLength={maxLength}
                disabled={disabled}
                onFocus={onFocus}
                onBlur={() => {
                    setShowDropdown(false);
                    onBlur?.();
                }}
            />
            {showDropdown && (
                <div className="mention-dropdown">
                    {loading ? (
                        <div className="mention-dropdown-hint">Đang tìm...</div>
                    ) : suggestions.length === 0 ? (
                        <div className="mention-dropdown-hint">Không tìm thấy người dùng.</div>
                    ) : (
                        suggestions.map((u) => (
                            <button
                                type="button"
                                key={u.id}
                                className="mention-dropdown-row"
                                // preventDefault so the textarea never blurs before the click registers.
                                onMouseDown={(e) => {
                                    e.preventDefault();
                                    selectMention(u);
                                }}
                            >
                                {u.full_name}
                            </button>
                        ))
                    )}
                </div>
            )}
        </div>
    );
}
