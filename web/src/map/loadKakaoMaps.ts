const SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';

let pending: Promise<void> | null = null;

/**
 * 카카오맵 SDK 를 한 번만 불러온다. `autoload=false` 로 받은 뒤 `kakao.maps.load` 로 초기화를 끝낸다.
 * 실패하면 다음 호출에서 다시 시도할 수 있도록 캐시를 비운다.
 */
export function loadKakaoMaps(appKey: string): Promise<void> {
  if (pending) {
    return pending;
  }
  pending = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.async = true;
    script.src = `${SDK_URL}?appkey=${encodeURIComponent(appKey)}&autoload=false`;
    script.onload = () => kakao.maps.load(resolve);
    script.onerror = () => {
      script.remove();
      reject(new Error('카카오맵 SDK 를 불러오지 못했어요. 키와 등록된 도메인을 확인해 주세요.'));
    };
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    pending = null;
    throw error;
  });
  return pending;
}
