import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

interface Options {
  /** 로그인하고 지도 목록을 불러온 뒤에만 처리한다 */
  ready: boolean;
  /** 지금 볼 수 있는 지도들. 이 밖의 지도에 대한 알림은 무시한다(공유가 풀렸거나 없어진 지도) */
  knownMapIds: readonly string[];
  onOpen: (mapId: string, pinId: string | null) => void;
}

/**
 * 알림을 눌러 들어온 주소(?map=지도&pin=핀)를 처리한다: 해당 지도(와 핀)를 열고, 주소에서는 지운다(새로고침해도 다시 열리지 않게).
 * 준비되기 전에는 주소를 그대로 두었다가 준비되면 한 번 처리한다.
 */
export function useOpenFromQuery({ ready, knownMapIds, onOpen }: Options): void {
  const location = useLocation();
  const navigate = useNavigate();
  const params = new URLSearchParams(location.search);
  const mapId = params.get('map');
  const pinId = params.get('pin');
  /** 매 렌더링마다 새로 만들어지는 콜백이어도 같은 알림을 두 번 처리하지 않도록, 최신 콜백만 들고 있고 의존성에서는 뺀다 */
  const latestOnOpen = useRef(onOpen);
  useEffect(() => {
    latestOnOpen.current = onOpen;
  });

  useEffect(() => {
    if (!ready || mapId === null) {
      return;
    }
    navigate(location.pathname, { replace: true });
    if (knownMapIds.includes(mapId)) {
      latestOnOpen.current(mapId, pinId);
    }
  }, [ready, mapId, pinId, knownMapIds, location.pathname, navigate]);
}
