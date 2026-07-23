import { describe, it, expect } from 'vitest';
import { normalizeGraphNodes, normalizeGraphLinks } from './graphNormalize';

describe('normalizeGraphNodes', () => {
    it('returns [] for missing/empty input', () => {
        expect(normalizeGraphNodes()).toEqual([]);
        expect(normalizeGraphNodes([])).toEqual([]);
    });

    it('prefers properties.name/title for label, falls back through keyword/name/id', () => {
        const [node] = normalizeGraphNodes([{ id: 'n1', properties: { name: 'React' } }]);
        expect(node.label).toBe('React');

        const [fallback] = normalizeGraphNodes([{ keyword: 'vue' }]);
        expect(fallback.id).toBe('vue');
        expect(fallback.label).toBe('vue');
    });

    it('lowercases type, preferring the first Neo4j label over type/category', () => {
        const [node] = normalizeGraphNodes([{ id: 'n1', labels: ['Company'], type: 'other' }]);
        expect(node.type).toBe('company');
    });

    it('defaults type to technology when nothing is provided', () => {
        const [node] = normalizeGraphNodes([{ id: 'n1' }]);
        expect(node.type).toBe('technology');
    });
});

describe('normalizeGraphLinks', () => {
    it('returns [] for missing/empty input', () => {
        expect(normalizeGraphLinks()).toEqual([]);
        expect(normalizeGraphLinks([])).toEqual([]);
    });

    it('maps source/target through source_id/from and target_id/to fallbacks', () => {
        const [link] = normalizeGraphLinks([{ source_id: 'a', target_id: 'b' }]);
        expect(link.source).toBe('a');
        expect(link.target).toBe('b');

        const [fallback] = normalizeGraphLinks([{ from: 'x', to: 'y' }]);
        expect(fallback.source).toBe('x');
        expect(fallback.target).toBe('y');
    });

    it('uppercases type, falling back through relation before defaulting to RELATED_TO', () => {
        const [link] = normalizeGraphLinks([{ relation: 'uses' }]);
        expect(link.type).toBe('USES');

        const [fallback] = normalizeGraphLinks([{}]);
        expect(fallback.type).toBe('RELATED_TO');
    });
});
