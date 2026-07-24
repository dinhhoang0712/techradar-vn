import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';
import type { ReactNode, JSX } from 'react';
import './MarkdownContent.css';

// Tag h1-h6 markdown đều dùng chung 1 class .md-heading (không phân biệt cỡ chữ theo cấp) — đúng
// hành vi của renderer cũ (utils/markdown.tsx) mà nơi này thay thế, tránh tiêu đề AI sinh ra to
// bất thường trong 1 bubble chat/card nhỏ.
function heading(Tag: keyof JSX.IntrinsicElements) {
    return function Heading({ children }: { children?: ReactNode }) {
        return <Tag className="md-heading">{children}</Tag>;
    };
}

const components: Components = {
    h1: heading('h3'),
    h2: heading('h4'),
    h3: heading('h5'),
    h4: heading('h6'),
    h5: heading('h6'),
    h6: heading('h6'),
    p: ({ children }) => <p className="md-p">{children}</p>,
    ul: ({ children }) => <ul className="md-list">{children}</ul>,
    ol: ({ children }) => <ol className="md-list">{children}</ol>,
    code: ({ children }) => <code className="md-code">{children}</code>,
    blockquote: ({ children }) => <blockquote className="md-blockquote">{children}</blockquote>,
    a: ({ children, href }) => (
        <a className="md-link" href={href} target="_blank" rel="noopener noreferrer">{children}</a>
    ),
    table: ({ children }) => (
        <div className="md-table-wrap"><table className="md-table">{children}</table></div>
    ),
    th: ({ children, style }) => <th style={style}>{children}</th>,
    td: ({ children, style }) => <td style={style}>{children}</td>,
};

interface MarkdownContentProps {
    children: string;
    className?: string;
}

// Renderer Markdown dùng chung cho mọi nội dung AI sinh ra (chat, báo cáo, insight công ty, roadmap
// sự nghiệp...) — dùng react-markdown + remark-gfm (hỗ trợ đúng bảng GFM) thay vì mỗi nơi tự parse
// 1 kiểu khác nhau (nguồn gốc bug bảng Markdown hiện thành text thô ở trang Báo cáo).
export default function MarkdownContent({ children, className }: MarkdownContentProps) {
    return (
        <div className={className}>
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
                {children}
            </ReactMarkdown>
        </div>
    );
}
