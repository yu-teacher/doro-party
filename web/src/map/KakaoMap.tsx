import { useEffect, useRef, useState } from 'react';
import { DEFAULT_MAP_CENTER, DEFAULT_MAP_LEVEL, KAKAO_MAP_APP_KEY } from '../config';
import { loadKakaoMaps } from './loadKakaoMaps';

type Status = 'loading' | 'ready' | 'error' | 'no-key';

/** 화면 가득 카카오맵을 그린다. 키가 없거나 SDK 를 못 불러오면 이유를 안내한다. */
export default function KakaoMap() {
  const container = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<Status>(KAKAO_MAP_APP_KEY ? 'loading' : 'no-key');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!KAKAO_MAP_APP_KEY) {
      return;
    }
    let cancelled = false;
    loadKakaoMaps(KAKAO_MAP_APP_KEY)
      .then(() => {
        if (cancelled || !container.current) {
          return;
        }
        new kakao.maps.Map(container.current, {
          center: new kakao.maps.LatLng(DEFAULT_MAP_CENTER.lat, DEFAULT_MAP_CENTER.lng),
          level: DEFAULT_MAP_LEVEL,
        });
        setStatus('ready');
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        console.error('Kakao map failed to load', error);
        setMessage(error instanceof Error ? error.message : '지도를 불러오지 못했어요.');
        setStatus('error');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="relative h-full w-full bg-slate-800">
      <div ref={container} className="absolute inset-0" aria-label="지도" role="application" />
      {status !== 'ready' && (
        <div className="absolute inset-0 flex items-center justify-center p-8 text-center">
          <p className="max-w-xs text-sm leading-relaxed text-slate-300">
            {status === 'loading' && '지도를 불러오는 중이에요…'}
            {status === 'no-key' && '카카오맵 키가 아직 설정되지 않았어요. 설정이 끝나면 여기에 지도가 보여요.'}
            {status === 'error' && message}
          </p>
        </div>
      )}
    </div>
  );
}
