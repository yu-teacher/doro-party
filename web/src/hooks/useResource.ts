import axios from 'axios';
import { useCallback, useEffect, useState } from 'react';

export interface Resource<T> {
  data: T | null;
  loading: boolean;
  /** 불러오지 못했을 때의 안내 문구 */
  error: string | null;
  /** 다시 불러온다(기존 데이터는 새 응답이 올 때까지 그대로 보여 준다). */
  reload: () => void;
}

/**
 * 화면에 필요한 서버 데이터 하나를 불러온다. `load` 가 바뀌거나 reload 하면 다시 요청하고, 이전 요청은 취소한다.
 * 호출하는 쪽은 `load` 를 useCallback 으로 안정화해서 넘긴다.
 */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>): Resource<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const request = new AbortController();
    setLoading(true);
    load(request.signal)
      .then((loaded) => {
        setData(loaded);
        setError(null);
      })
      .catch((failure: unknown) => {
        if (axios.isCancel(failure)) {
          return;
        }
        console.error('Failed to load resource', failure);
        setError(failure instanceof Error ? failure.message : '불러오지 못했어요.');
      })
      .finally(() => {
        if (!request.signal.aborted) {
          setLoading(false);
        }
      });
    return () => request.abort();
  }, [load, version]);

  const reload = useCallback(() => setVersion((current) => current + 1), []);
  return { data, loading, error, reload };
}
