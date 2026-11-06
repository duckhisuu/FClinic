import { useEffect, useRef, useState } from 'react';
export function useResource(load, dependencies) {
  const key = JSON.stringify(dependencies); const source = dependencies[0]; const sequence = useRef(0);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState({ key: '', source: null, loading: true, data: null, error: null });
  useEffect(() => {
    const ticket = ++sequence.current; const controller = new AbortController();
    setState({ key, source, loading: true, data: null, error: null });
    Promise.resolve().then(() => load(controller.signal)).then(data => {
      if (!controller.signal.aborted && sequence.current === ticket) setState({ key, source, loading: false, data, error: null });
    }).catch(error => {
      if (!controller.signal.aborted && sequence.current === ticket) setState({ key, source, loading: false, data: null, error });
    });
    return () => { controller.abort(); };
  }, [key, attempt, ...dependencies.filter(x => typeof x === 'object' || typeof x === 'function')]);
  return { ...(state.key === key && state.source === source ? state : { loading: true, data: null, error: null }), reload: () => setAttempt(n => n + 1) };
}
