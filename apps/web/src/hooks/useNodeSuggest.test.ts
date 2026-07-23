import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useNodeSuggest } from './useNodeSuggest';

const nodes = [
    { id: 'react', label: 'React' },
    { id: 'react-native', label: 'React Native' },
    { id: 'vue', label: 'Vue' },
];

describe('useNodeSuggest', () => {
    it('returns [] for an empty query', () => {
        const { result } = renderHook(() => useNodeSuggest('', nodes, null));
        expect(result.current).toEqual([]);
    });

    it('filters nodes case-insensitively by label/id, capped at 5', () => {
        const { result } = renderHook(() => useNodeSuggest('react', nodes, null));
        expect(result.current.map(n => n.id)).toEqual(['react', 'react-native']);
    });

    it('falls back to id when a node has no label', () => {
        const noLabelNodes = [{ id: 'golang' }];
        const { result } = renderHook(() => useNodeSuggest('go', noLabelNodes, null));
        expect(result.current).toEqual(noLabelNodes);
    });

    it('hides suggestions once the query exactly matches the active selection', () => {
        const { result } = renderHook(() => useNodeSuggest('React', nodes, 'React'));
        expect(result.current).toEqual([]);
    });

    it('is case-insensitive when comparing against the active selection', () => {
        const { result } = renderHook(() => useNodeSuggest('  react  '.trim(), nodes, 'REACT'));
        expect(result.current).toEqual([]);
    });

    it('still suggests when query differs from the active selection', () => {
        const { result } = renderHook(() => useNodeSuggest('vue', nodes, 'React'));
        expect(result.current.map(n => n.id)).toEqual(['vue']);
    });

    it('caps results at 5 matches', () => {
        const manyNodes = Array.from({ length: 10 }, (_, i) => ({ id: `node-${i}`, label: `Node ${i}` }));
        const { result } = renderHook(() => useNodeSuggest('node', manyNodes, null));
        expect(result.current).toHaveLength(5);
    });
});
