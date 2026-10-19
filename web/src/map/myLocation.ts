/** 내 위치 표시(파란 점과 정확도 원). 위치는 이 기기 안에서만 쓰고 서버로 보내지 않는다. */
export const MY_LOCATION_COLOR = '#2563EB';

/** 점의 지름(px). 점 바깥으로 흰 테두리와 퍼지는 고리가 더해진다. */
export const MY_LOCATION_DOT_PX = 16;
const MY_LOCATION_BOX_PX = 44;

/**
 * 정확도 원을 그릴 범위(미터). 이보다 작으면 점과 겹쳐 의미가 없고, 이보다 크면(컴퓨터의 IP 기반 위치 등) 지도를 뒤덮어
 * 오히려 "이 근처 어딘가" 라는 사실을 가리므로 그리지 않는다.
 */
export const MIN_ACCURACY_CIRCLE_M = 25;
export const MAX_ACCURACY_CIRCLE_M = 1000;

export interface MyLocation {
  lat: number;
  lng: number;
  /** 위치 오차 반경(미터). 브라우저가 알려 주는 값 */
  accuracy: number;
}

/** 정확도 원의 반지름(미터). 그릴 필요가 없으면 null. */
export function accuracyCircleRadius(accuracy: number): number | null {
  if (!Number.isFinite(accuracy) || accuracy < MIN_ACCURACY_CIRCLE_M || accuracy > MAX_ACCURACY_CIRCLE_M) {
    return null;
  }
  return accuracy;
}

/**
 * 내 위치 마커: 파란 점 + 흰 테두리 + 천천히 퍼지는 고리(움직임을 줄이는 설정이면 멈춘다).
 * 지도 클릭을 가로막지 않고(pointer-events: none), 화면 읽기 도구에는 "내 위치" 로 읽힌다.
 */
export function buildMyLocationElement(): HTMLElement {
  const box = document.createElement('div');
  box.setAttribute('role', 'img');
  box.setAttribute('aria-label', '내 위치');
  box.style.cssText = `position:relative;width:${MY_LOCATION_BOX_PX}px;height:${MY_LOCATION_BOX_PX}px;pointer-events:none;`;

  const pulse = document.createElement('span');
  pulse.className = 'my-location-pulse';
  pulse.style.cssText = `position:absolute;inset:0;border-radius:50%;background:${MY_LOCATION_COLOR};opacity:.25;`;

  const dot = document.createElement('span');
  const offset = (MY_LOCATION_BOX_PX - MY_LOCATION_DOT_PX) / 2;
  dot.style.cssText = `position:absolute;top:${offset}px;left:${offset}px;width:${MY_LOCATION_DOT_PX}px;height:${MY_LOCATION_DOT_PX}px;`
    + `border-radius:50%;background:${MY_LOCATION_COLOR};border:3px solid #fff;box-sizing:border-box;box-shadow:0 1px 5px rgba(0,0,0,.5);`;

  box.append(pulse, dot);
  return box;
}
