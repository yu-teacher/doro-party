import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PartyMap, Pin } from '../api/types';
import NearbySheet from './NearbySheet';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const getPinsOfMaps = vi.hoisted(() => vi.fn<(ids: string[], chunk: number, signal?: AbortSignal) => Promise<Pin[]>>());
vi.mock('../api/socialApi', () => ({ getPinsOfMaps }));

const HERE = { lat: 37.5563, lng: 126.9236 };
const northOf = (meters: number) => ({ lat: HERE.lat + meters / 111_195, lng: HERE.lng });

function pin(id: string, partial: Partial<Pin> = {}): Pin {
  return {
    id,
    mapId: 'm1',
    createdBy: 'u1',
    authorNickname: '나',
    authorColor: '#14B8A6',
    lat: HERE.lat,
    lng: HERE.lng,
    name: id,
    sharedMemo: null,
    status: 'WISH',
    rating: null,
    revisitIntent: null,
    tags: [],
    visitCount: 0,
    lastVisitedOn: null,
    photoCount: 0,
    createdAt: '2026-10-07T00:00:00Z',
    updatedAt: '2026-10-07T00:00:00Z',
    ...partial,
  };
}

function map(id: string, name: string): PartyMap {
  return { id, name, description: null, ownerId: 'u1', ownerNickname: '나', ownerColor: '#14B8A6', mine: true, role: 'OWNER', viaGroups: [], friendAccess: 'NONE', pinCount: 0, createdAt: '', updatedAt: '' };
}

const MAPS = [map('m1', '맛집'), map('m2', '친구 지도')];

let container: HTMLDivElement;
let root: Root;
const original = Object.getOwnPropertyDescriptor(navigator, 'geolocation');

function stubLocation(behavior: 'ok' | 'denied') {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: (ok: (p: GeolocationPosition) => void, fail: (e: GeolocationPositionError) => void) => {
        if (behavior === 'ok') {
          ok({ coords: { latitude: HERE.lat, longitude: HERE.lng } } as GeolocationPosition);
        } else {
          fail({ code: 1 } as GeolocationPositionError);
        }
      },
    },
  });
}

async function render(onPick: (pin: Pin) => void = () => undefined) {
  await act(async () => {
    root.render(<NearbySheet maps={MAPS} onPick={onPick} onClose={() => undefined} />);
  });
}

const names = () => Array.from(container.querySelectorAll('ul li button')).map((button) => button.querySelector('span span')?.textContent ?? '');
/** 칩 하나. "전체" 처럼 여러 줄에 있는 이름은 줄(toolbar 의 aria-label)로 구분한다. */
const chip = (label: string, toolbar?: string) => {
  const scope = toolbar === undefined ? container : (container.querySelector(`[role="toolbar"][aria-label="${toolbar}"]`) as HTMLElement);
  return Array.from(scope.querySelectorAll('button')).find((button) => button.textContent?.trim() === label) as HTMLButtonElement;
};
const click = (button: HTMLElement) => act(() => button.dispatchEvent(new MouseEvent('click', { bubbles: true })));

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  getPinsOfMaps.mockReset();
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

describe('NearbySheet', () => {
  it('내가 볼 수 있는 모든 지도의 핀을 불러와 가까운 순으로 보여 준다(기본 반경 1km)', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue([
      pin('멀리', northOf(2500)),
      pin('가까이', { ...northOf(100), mapId: 'm2' }),
      pin('중간', northOf(800)),
    ]);
    await render();
    expect(getPinsOfMaps).toHaveBeenCalledWith(['m1', 'm2'], expect.any(Number), expect.anything());
    expect(names()).toEqual(['가까이', '중간']);
    expect(container.textContent).toContain('100m');
    expect(container.textContent).toContain('800m');
    expect(container.textContent).toContain('친구 지도');
  });

  it('반경을 "전체"로 바꾸면 먼 핀도 보인다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue([pin('가까이', northOf(100)), pin('멀리', northOf(2500))]);
    await render();
    expect(names()).toEqual(['가까이']);
    click(chip('전체', '반경'));
    expect(names()).toEqual(['가까이', '멀리']);
    expect(container.textContent).toContain('2.5km');
  });

  it('상태와 태그로 거른다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue([
      pin('카페가고싶다', { ...northOf(100), tags: ['카페'] }),
      pin('카페다녀옴', { ...northOf(200), tags: ['카페'], status: 'VISITED' }),
      pin('술집', { ...northOf(300), tags: ['술집'] }),
    ]);
    await render();
    expect(names()).toEqual(['카페가고싶다', '카페다녀옴', '술집']);
    click(chip('다녀왔어요', '핀 필터'));
    expect(names()).toEqual(['카페다녀옴']);
    click(chip('전체', '핀 필터'));
    click(chip('#카페'));
    expect(names()).toEqual(['카페가고싶다', '카페다녀옴']);
    click(chip('#카페'));
    expect(names()).toHaveLength(3);
  });

  it('항목을 누르면 그 핀을 넘겨 준다', async () => {
    stubLocation('ok');
    const target = pin('가까이', northOf(100));
    getPinsOfMaps.mockResolvedValue([target]);
    const onPick = vi.fn();
    await render(onPick);
    click(container.querySelector('ul li button') as HTMLElement);
    expect(onPick).toHaveBeenCalledWith(target);
  });

  it('반경 안에 없으면 가장 가까운 핀이 얼마나 떨어져 있는지 알려 준다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue([pin('멀리', northOf(4000))]);
    await render();
    expect(container.textContent).toContain('조건에 맞는 핀이 없어요');
    expect(container.textContent).toContain('4.0km');
    expect(container.textContent).toContain('멀리');
  });

  it('핀이 하나도 없으면 안내한다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue([]);
    await render();
    expect(container.textContent).toContain('아직 핀이 없어요');
  });

  it('위치 권한이 거부되면 이유를 안내하고 목록은 보여 주지 않는다', async () => {
    stubLocation('denied');
    getPinsOfMaps.mockResolvedValue([pin('가까이', northOf(100))]);
    await render();
    expect(container.textContent).toContain('위치 권한이 꺼져 있어요');
    expect(names()).toEqual([]);
  });

  it('핀을 불러오지 못하면 다시 시도할 수 있다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockRejectedValueOnce(new Error('서버 오류'));
    getPinsOfMaps.mockResolvedValueOnce([pin('가까이', northOf(100))]);
    await render();
    expect(container.textContent).toContain('서버 오류');
    await act(async () => {
      chip('다시 시도').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    expect(names()).toEqual(['가까이']);
  });

  it('50개를 넘으면 끊어서 보여 주고 더 보기로 이어 본다', async () => {
    stubLocation('ok');
    getPinsOfMaps.mockResolvedValue(Array.from({ length: 120 }, (_, i) => pin(`p${String(i).padStart(3, '0')}`, northOf(10 + i * 3))));
    await render();
    expect(names()).toHaveLength(50);
    click(chip('더 보기 (70개 남음)'));
    expect(names()).toHaveLength(100);
  });
});
