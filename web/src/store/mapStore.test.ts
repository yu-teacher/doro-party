import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as mapsApi from '../api/mapsApi';
import type { PartyMap, Pin, PinInput } from '../api/types';
import { filterPins, useMapStore } from './mapStore';

vi.mock('../api/mapsApi');
const api = vi.mocked(mapsApi);

function map(id: string, pinCount = 0): PartyMap {
  return { id, name: `지도 ${id}`, description: null, ownerId: 'u', mine: true, pinCount, createdAt: '', updatedAt: '' };
}

function pin(id: string, mapId: string, overrides: Partial<Pin> = {}): Pin {
  return {
    id, mapId, createdBy: 'u', lat: 37.5, lng: 127, name: `핀 ${id}`, sharedMemo: null,
    status: 'WISH', rating: null, tags: [], createdAt: '', updatedAt: '', ...overrides,
  };
}

const INPUT: PinInput = { name: '새 핀', sharedMemo: null, lat: 37.5, lng: 127, status: 'WISH', rating: null, tags: [] };

beforeEach(() => {
  window.localStorage.clear();
  useMapStore.getState().reset();
  vi.resetAllMocks();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('loadMaps', () => {
  it('마지막으로 보던 지도를 열고, 없으면 첫 지도를 연다', async () => {
    api.listMyMaps.mockResolvedValue([map('a'), map('b')]);
    api.listPins.mockResolvedValue([pin('p1', 'b')]);
    window.localStorage.setItem('party.selectedMapId', 'b');

    await useMapStore.getState().loadMaps();

    expect(useMapStore.getState().selectedMapId).toBe('b');
    expect(useMapStore.getState().pins).toHaveLength(1);

    useMapStore.getState().reset();
    window.localStorage.setItem('party.selectedMapId', 'gone');
    await useMapStore.getState().loadMaps();
    expect(useMapStore.getState().selectedMapId).toBe('a');
  });

  it('지도가 하나도 없으면 아무것도 고르지 않는다', async () => {
    api.listMyMaps.mockResolvedValue([]);

    await useMapStore.getState().loadMaps();

    expect(useMapStore.getState()).toMatchObject({ mapsLoaded: true, selectedMapId: null, pins: [] });
    expect(api.listPins).not.toHaveBeenCalled();
  });

  it('목록을 못 불러오면 오류 문구를 남기고 불러오기는 끝난 것으로 본다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    api.listMyMaps.mockRejectedValue(new Error('서버 오류'));

    await useMapStore.getState().loadMaps();

    expect(useMapStore.getState()).toMatchObject({ mapsLoaded: true, error: '서버 오류' });
  });
});

describe('selectMap', () => {
  it('지도를 바꾸면 핀과 필터가 초기화되고, 늦게 도착한 이전 지도의 응답은 무시한다', async () => {
    let resolveFirst: (pins: Pin[]) => void = () => undefined;
    api.listPins.mockImplementationOnce(() => new Promise<Pin[]>((resolve) => { resolveFirst = resolve; }));
    api.listPins.mockResolvedValueOnce([pin('second', 'b')]);

    const first = useMapStore.getState().selectMap('a');
    useMapStore.getState().setTagFilter('x');
    await useMapStore.getState().selectMap('b');
    resolveFirst([pin('stale', 'a')]);
    await first;

    expect(useMapStore.getState().pins.map((p) => p.id)).toEqual(['second']);
    expect(useMapStore.getState().tagFilter).toBeNull();
  });

  it('요청이 취소된 오류는 화면에 오류로 보여주지 않는다', async () => {
    const cancel = new axios.CanceledError('canceled');
    api.listPins.mockRejectedValue(cancel);

    await useMapStore.getState().selectMap('a');

    expect(useMapStore.getState().error).toBeNull();
  });
});

describe('핀 변경', () => {
  beforeEach(async () => {
    api.listMyMaps.mockResolvedValue([map('a', 1)]);
    api.listPins.mockResolvedValue([pin('p1', 'a')]);
    await useMapStore.getState().loadMaps();
  });

  it('핀을 추가하면 목록과 지도의 핀 개수가 함께 늘어난다', async () => {
    api.createPin.mockResolvedValue(pin('p2', 'a'));

    await useMapStore.getState().addPin(INPUT);

    expect(useMapStore.getState().pins.map((p) => p.id)).toEqual(['p1', 'p2']);
    expect(useMapStore.getState().maps[0].pinCount).toBe(2);
  });

  it('핀을 수정하고 삭제하면 목록이 따라 바뀐다', async () => {
    api.updatePin.mockResolvedValue(pin('p1', 'a', { name: '수정됨' }));
    api.deletePin.mockResolvedValue();

    await useMapStore.getState().editPin('p1', INPUT);
    expect(useMapStore.getState().pins[0].name).toBe('수정됨');

    await useMapStore.getState().removePin('p1');
    expect(useMapStore.getState().pins).toEqual([]);
    expect(useMapStore.getState().maps[0].pinCount).toBe(0);
  });

  it('서버가 거부하면 목록을 바꾸지 않고 오류를 호출자에게 넘긴다', async () => {
    api.createPin.mockRejectedValue(new Error('핀이 너무 많아요'));

    await expect(useMapStore.getState().addPin(INPUT)).rejects.toThrow('핀이 너무 많아요');
    expect(useMapStore.getState().pins).toHaveLength(1);
  });
});

describe('지도 만들기·삭제', () => {
  it('새 지도를 만들면 그 지도가 열리고, 열려 있던 지도를 지우면 다음 지도로 간다', async () => {
    api.listMyMaps.mockResolvedValue([map('a')]);
    api.listPins.mockResolvedValue([]);
    await useMapStore.getState().loadMaps();
    api.createMap.mockResolvedValue(map('b'));

    await useMapStore.getState().createMap({ name: 'b', description: null });
    expect(useMapStore.getState().selectedMapId).toBe('b');
    expect(useMapStore.getState().maps.map((m) => m.id)).toEqual(['b', 'a']);

    api.deleteMap.mockResolvedValue();
    await useMapStore.getState().removeMap('b');
    expect(useMapStore.getState().selectedMapId).toBe('a');
  });
});

describe('filterPins', () => {
  const pins = [
    pin('1', 'a', { status: 'WISH', tags: ['술집'] }),
    pin('2', 'a', { status: 'VISITED', tags: ['술집', '추천'] }),
    pin('3', 'a', { status: 'VISITED', tags: ['카페'] }),
  ];

  it('상태와 태그를 함께 적용한다', () => {
    expect(filterPins(pins, 'ALL', null)).toHaveLength(3);
    expect(filterPins(pins, 'VISITED', null).map((p) => p.id)).toEqual(['2', '3']);
    expect(filterPins(pins, 'ALL', '술집').map((p) => p.id)).toEqual(['1', '2']);
    expect(filterPins(pins, 'VISITED', '술집').map((p) => p.id)).toEqual(['2']);
  });
});
