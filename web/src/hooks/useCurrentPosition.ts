import { useCallback, useEffect, useState } from 'react';
import { LOCATE_TIMEOUT_MS, LOCATE_UNSUPPORTED, locateErrorMessage } from '../utils/geolocation';
import type { LatLng } from '../utils/nearby';

export type PositionState =
  | { status: 'locating' }
  | { status: 'ready'; position: LatLng }
  | { status: 'error'; message: string };

/**
 * 내 위치를 한 번만 확인한다(계속 추적하지 않는다). 위치는 이 훅을 쓰는 화면의 상태로만 두고 서버로 보내지 않는다.
 * retry 를 부르면 다시 확인한다.
 */
export function useCurrentPosition(): { state: PositionState; retry: () => void } {
  const [state, setState] = useState<PositionState>({ status: 'locating' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState({ status: 'locating' });
    if (!('geolocation' in navigator)) {
      setState({ status: 'error', message: LOCATE_UNSUPPORTED });
      return undefined;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (!cancelled) {
          setState({ status: 'ready', position: { lat: position.coords.latitude, lng: position.coords.longitude } });
        }
      },
      (failure) => {
        if (!cancelled) {
          setState({ status: 'error', message: locateErrorMessage(failure.code) });
        }
      },
      { enableHighAccuracy: true, timeout: LOCATE_TIMEOUT_MS },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);
  return { state, retry };
}
