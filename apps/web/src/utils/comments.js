// Comments come back as a flat list (backend convention: no nested JSON). Group them client-side
// into top-level comments + a lookup of replies per top-level parent id.
export function groupComments(comments) {
    const topLevel = [];
    const repliesByParentId = new Map();
    for (const c of comments || []) {
        if (!c.parent_id) {
            topLevel.push(c);
        } else {
            if (!repliesByParentId.has(c.parent_id)) {
                repliesByParentId.set(c.parent_id, []);
            }
            repliesByParentId.get(c.parent_id).push(c);
        }
    }
    return { topLevel, repliesByParentId };
}
