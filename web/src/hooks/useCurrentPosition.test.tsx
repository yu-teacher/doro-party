import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCurrentPosition } from './useCurrentPosition';
import type { PositionState } from './useCurrentPosition';
import { LOCATE_ERRORS, LOCATE_FAILED, LOCATE_UNSUPPORTED } from '../utils/geolocation';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Success = (position: GeolocationPosition) => void;
type Failure = (error: GeolocationPositionError) => void;

let latest: { state: PositionState; retry: () => void };
function Probe() {
  latest = useCurrentPosition();
  return null;
}

let container: HTMLDivElement;
let root: Root;
const original = Object.getOwnPropertyDescriptor(navigator, 'geolocation');

function stubGeolocation(getCurrentPosition: (ok: Success, fail: Failure) => void) {
  Object.defineProperty(navigator, 'geolocation', { configurable: true, value: { getCurrentPosition } });
}

const fix = (lat: number, lng: number) => ({ coords: { latitude: lat, longitude: lng } }) as GeolocationPosition;
const failure = (code: number) => ({ code }) as GeolocationPositionError;

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  if (original) {
    Object.defineProperty(navigator, 'geolocation', original);
  } else {
    Reflect.deleteProperty(navigator, 'geolocation');
  }
});

describe('useCurrentPosition', () => {
  it('위치를 받으면 ready 가 된다', () => {
    stubGeolocation((ok) => ok(fix(37.55, 126.92)));
    act(() => root.render(<Probe />));
    expect(latest.state).toEqual({ status: 'ready', position: { lat: 37.55, lng: 126.92 } });
  });

  it('응답을 기다리는 동안은 locating', () => {
    stubGeolocation(() => undefined);
    act(() => root.render(<Probe />));
    expect(latest.state).toEqual({ status: 'locating' });
  });

  it.each([1, 2, 3])('실패 코드 %d 는 안내 문구로 바뀐다', (code) => {
    stubGeolocation((_ok, fail) => fail(failure(code)));
    act(() => root.render(<Probe />));
    expect(latest.state).toEqual({ status: 'error', message: LOCATE_ERRORS[code] });
  });

  it('알 수 없는 실패 코드는 일반 문구', () => {
    stubGeolocation((_ok, fail) => fail(failure(99)));
    act(() => root.render(<Probe />));
    expect(latest.state).toEqual({ status: 'error', message: LOCATE_FAILED });
  });

  it('위치 기능이 없는 브라우저는 안내한다', () => {
    Reflect.deleteProperty(navigator, 'geolocation');
    act(() => root.render(<Probe />));
    expect(latest.state).toEqual({ status: 'error', message: LOCATE_UNSUPPORTED });
  });

  it('retry 를 부르면 다시 확인한다(실패 후 성공)', () => {
    const request = vi.fn<(ok: Success, fail: Failure) => void>();
    request.mockImplementationOnce((_ok, fail) => fail(failure(1)));
    request.mockImplementationOnce((ok) => ok(fix(35.1, 129.0)));
    stubGeolocation(request);
    act(() => root.render(<Probe />));
    expect(latest.state.status).toBe('error');
    act(() => latest.retry());
    expect(request).toHaveBeenCalledTimes(2);
    expect(latest.state).toEqual({ status: 'ready', position: { lat: 35.1, lng: 129.0 } });
  });

  it('닫힌 뒤(unmount)에 늦게 온 응답은 무시한다', () => {
    let deliver: Success = () => undefined;
    stubGeolocation((ok) => {
      deliver = ok;
    });
    act(() => root.render(<Probe />));
    act(() => root.unmount());
    expect(() => deliver(fix(1, 2))).not.toThrow();
    root = createRoot(container);
  });
});
