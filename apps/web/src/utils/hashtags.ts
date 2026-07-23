// Splits post content into plain-text and hashtag tokens, matched against the post's own
// canonical `hashtags` array (not a re-implemented parser) so anything highlighted is always
// exactly what the feed can filter on.
export interface TextToken {
    type: 'text';
    value: string;
}

export interface TagToken {
    type: 'tag';
    value: string;
    raw: string;
}

export type HashtagToken = TextToken | TagToken;

export function tokenizeHashtags(content: string | undefined | null, hashtags: string[] | undefined | null): HashtagToken[] {
    if (!content || !hashtags || hashtags.length === 0) {
        return [{ type: 'text', value: content || '' }];
    }
    const escaped = hashtags.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    // Negative lookahead instead of \b: \b is ASCII-word-based in JS and misbehaves right after
    // a Vietnamese diacritic letter (not part of JS's \w), which would silently break matching.
    const pattern = new RegExp(`#(${escaped.join('|')})(?![\\p{L}\\p{N}_])`, 'giu');

    const tokens: HashtagToken[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(content)) !== null) {
        if (match.index > lastIndex) {
            tokens.push({ type: 'text', value: content.slice(lastIndex, match.index) });
        }
        tokens.push({ type: 'tag', value: match[1].toLowerCase(), raw: match[0] });
        lastIndex = pattern.lastIndex;
    }
    if (lastIndex < content.length) {
        tokens.push({ type: 'text', value: content.slice(lastIndex) });
    }
    return tokens;
}
