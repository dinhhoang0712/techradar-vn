// Comments come back as a flat list (backend convention: no nested JSON). Group them client-side
// into top-level comments + a lookup of replies per top-level parent id.
interface CommentLike {
    id: string | number;
    parent_id?: string | number | null;
}

export function groupComments<T extends CommentLike>(
    comments: T[] | null | undefined,
): { topLevel: T[]; repliesByParentId: Map<string | number, T[]> } {
    const topLevel: T[] = [];
    const repliesByParentId = new Map<string | number, T[]>();
    for (const c of comments || []) {
        if (!c.parent_id) {
            topLevel.push(c);
        } else {
            if (!repliesByParentId.has(c.parent_id)) {
                repliesByParentId.set(c.parent_id, []);
            }
            repliesByParentId.get(c.parent_id)!.push(c);
        }
    }
    return { topLevel, repliesByParentId };
}
