import { describe, expect, it } from 'vitest';
import { MAX_ACCURACY_CIRCLE_M, MIN_ACCURACY_CIRCLE_M, accuracyCircleRadius, buildMyLocationElement } from './myLocation';

describe('정확도 원', () => {
  it('의미 있는 범위의 정확도는 그대로 반지름이 된다', () => {
    expect(accuracyCircleRadius(80)).toBe(80);
    expect(accuracyCircleRadius(MIN_ACCURACY_CIRCLE_M)).toBe(MIN_ACCURACY_CIRCLE_M);
    expect(accuracyCircleRadius(MAX_ACCURACY_CIRCLE_M)).toBe(MAX_ACCURACY_CIRCLE_M);
  });

  it('너무 정확하면(점과 겹침) 그리지 않는다', () => {
    expect(accuracyCircleRadius(MIN_ACCURACY_CIRCLE_M - 1)).toBeNull();
    expect(accuracyCircleRadius(5)).toBeNull();
  });

  it('너무 부정확하면(IP 기반 위치 등 지도를 뒤덮음) 그리지 않는다', () => {
    expect(accuracyCircleRadius(MAX_ACCURACY_CIRCLE_M + 1)).toBeNull();
    expect(accuracyCircleRadius(25_000)).toBeNull();
  });

  it('숫자가 아닌 값은 그리지 않는다', () => {
    expect(accuracyCircleRadius(Number.NaN)).toBeNull();
    expect(accuracyCircleRadius(Number.POSITIVE_INFINITY)).toBeNull();
    expect(accuracyCircleRadius(-10)).toBeNull();
  });
});

describe('내 위치 마커 요소', () => {
  const element = buildMyLocationElement();

  it('화면 읽기 도구에는 "내 위치" 이미지로 읽힌다', () => {
    expect(element.getAttribute('role')).toBe('img');
    expect(element.getAttribute('aria-label')).toBe('내 위치');
  });

  it('지도 클릭(핀 꽂기)을 가로막지 않는다', () => {
    expect(element.style.pointerEvents).toBe('none');
  });

  it('점과 퍼지는 고리로 이루어진다', () => {
    expect(element.querySelector('.my-location-pulse')).not.toBeNull();
    expect(element.children).toHaveLength(2);
  });
});
