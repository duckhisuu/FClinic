import React from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { expect, it } from 'vitest';
import { useResource } from '../hooks/useResource';
it('aborts and ignores stale responses even when a provider ignores AbortSignal', async () => {
  const requests = [];
  const load = (query, signal) => new Promise(resolve => requests.push({ query, signal, resolve }));
  const { result, rerender } = renderHook(({ query }) => useResource(signal => load(query, signal), [query]), { initialProps: { query: 'old' } });
  await waitFor(() => expect(requests).toHaveLength(1)); rerender({ query: 'new' });
  await waitFor(() => expect(requests).toHaveLength(2)); expect(requests[0].signal.aborted).toBe(true);
  await act(async () => requests[1].resolve('fresh')); expect(result.current.data).toBe('fresh');
  await act(async () => requests[0].resolve('stale')); expect(result.current.data).toBe('fresh');
  // Same mode/JSON shape but a different host session API must hide prior data on the very first render.
  const first = { mode: 'live' }, second = { mode: 'live' }; const transitions = [];
  const provider = renderHook(({ api }) => { const state = useResource(async () => api === first ? 'old session data' : new Promise(() => {}), [api]); transitions.push({ api, data: state.data }); return state; }, { initialProps: { api: first } });
  await waitFor(() => expect(provider.result.current.data).toBe('old session data'));
  provider.rerender({ api: second });
  expect(transitions.filter(t => t.api === second).every(t => t.data === null)).toBe(true);
});
