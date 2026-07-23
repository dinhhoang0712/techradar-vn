// Shared Markdown renderer for AI-generated text (chat answers, interview summaries, career
// roadmaps) — tables, headings, nested bullets, bold/italic/code. Used by ChatbotPage,
// InterviewPage and CareerPage so the AI pages render markdown identically instead of each
// maintaining its own copy.
import type { ReactNode, JSX } from 'react';

export function renderMarkdown(text: string): ReactNode[] {
    const lines = text.split('\n');
    const elements: ReactNode[] = [];
    let i = 0;
    while (i < lines.length) {
        const line = lines[i];
        if (line.includes('|') && lines[i + 1]?.includes('---')) {
            const headers = line.split('|').filter(h => h.trim());
            const rows: string[][] = [];
            i += 2;
            while (i < lines.length && lines[i].includes('|')) {
                rows.push(lines[i].split('|').filter(c => c.trim()));
                i++;
            }
            elements.push(
                <table key={i} className="md-table">
                    <thead><tr>{headers.map((h, j) => <th key={j}>{inlineMarkdown(h.trim())}</th>)}</tr></thead>
                    <tbody>{rows.map((r, j) => <tr key={j}>{r.map((c, k) => <td key={k}>{inlineMarkdown(c.trim())}</td>)}</tr>)}</tbody>
                </table>
            );
        } else if (/^#{1,3} /.test(line)) {
            const level = line.match(/^#+/)![0].length;
            const content = line.replace(/^#+\s/, '');
            const Tag = `h${Math.min(level + 2, 6)}` as keyof JSX.IntrinsicElements;
            elements.push(<Tag key={i} className="md-heading">{inlineMarkdown(content)}</Tag>);
            i++;
        } else if (/^\s*[*-]\s+/.test(line)) {
            // Bullets nhận cả dòng thụt lề (sub-item, vd "    * ...") — nếu không strip phần
            // "*   " thừa này trước khi đưa vào inlineMarkdown, dấu * lẻ sẽ ăn nhầm vào cặp
            // **bold** ngay sau nó và làm hỏng cả dòng.
            const items: ReactNode[] = [];
            while (i < lines.length && /^\s*[*-]\s+/.test(lines[i])) {
                const match = lines[i].match(/^(\s*)[*-]\s+(.*)$/);
                const depth = match ? Math.min(Math.floor(match[1].length / 2), 3) : 0;
                const content = match ? match[2] : lines[i];
                items.push(
                    <li
                        key={i}
                        style={depth > 0 ? { marginLeft: `${depth * 16}px`, listStyleType: 'circle' } : undefined}
                    >
                        {inlineMarkdown(content)}
                    </li>
                );
                i++;
            }
            elements.push(<ul key={`ul-${i}`} className="md-list">{items}</ul>);
        } else if (line.trim() === '') {
            elements.push(<br key={`br-${i}`} />);
            i++;
        } else {
            elements.push(<p key={i} className="md-p">{inlineMarkdown(line)}</p>);
            i++;
        }
    }
    return elements;
}

function inlineMarkdown(text: string): ReactNode[] {
    const parts: ReactNode[] = [];
    let rest = text;
    let key = 0;
    while (rest.length > 0) {
        const boldMatch   = rest.match(/\*\*(.+?)\*\*/);
        const italicMatch = rest.match(/\*(.+?)\*/);
        const codeMatch   = rest.match(/`(.+?)`/);
        const earliest = [
            boldMatch   ? { idx: rest.indexOf(boldMatch[0]),   len: boldMatch[0].length,   el: <strong key={key++}>{boldMatch[1]}</strong> }   : null,
            italicMatch ? { idx: rest.indexOf(italicMatch[0]), len: italicMatch[0].length, el: <em key={key++}>{italicMatch[1]}</em> }           : null,
            codeMatch   ? { idx: rest.indexOf(codeMatch[0]),   len: codeMatch[0].length,   el: <code key={key++} className="md-code">{codeMatch[1]}</code> } : null,
        ].filter((x): x is { idx: number; len: number; el: JSX.Element } => x !== null).sort((a, b) => a.idx - b.idx)[0];
        if (!earliest) { parts.push(rest); break; }
        if (earliest.idx > 0) parts.push(rest.slice(0, earliest.idx));
        parts.push(earliest.el);
        rest = rest.slice(earliest.idx + earliest.len);
    }
    return parts;
}
