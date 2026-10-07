import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as mapsApi from '../api/mapsApi';
import * as recordsApi from '../api/recordsApi';
import type { PartyMap, Pin, PinInput } from '../api/types';
import { filterPins, useMapStore } from './mapStore';

vi.mock('../api/mapsApi');
vi.mock('../api/recordsApi');
const api = vi.mocked(mapsApi);
const records = vi.mocked(recordsApi);

function map(id: string, pinCount = 0): PartyMap {
  return {
    id, name: `지도 ${id}`, description: null, ownerId: 'u', ownerNickname: '주인', ownerColor: '#E4572E', mine: true, role: 'OWNER', viaGroups: [], friendAccess: 'NONE',
    pinCount, createdAt: '', updatedAt: '',
  };
}

function pin(id: string, mapId: string, overrides: Partial<Pin> = {}): Pin {
  return {
    id, mapId, createdBy: 'u', authorNickname: '작성자', authorColor: '#17BEBB', lat: 37.5, lng: 127, name: `핀 ${id}`, sharedMemo: null,
    status: 'WISH', rating: null, revisitIntent: null, tags: [], visitCount: 0, lastVisitedOn: null, photoCount: 0,
    createdAt: '', updatedAt: '', ...overrides,
  };
}

const INPUT: PinInput = { name: '새 핀', sharedMemo: null, lat: 37.5, lng: 127, status: 'WISH', rating: null, revisitIntent: null, tags: [] };

beforeEach(() => {
  window.localStorage.clear();
  useMapStore.getState().reset();
  vi.resetAllMocks();
  records.listPrivateNotes.mockResolvedValue([]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('친구 지도 둘러보기에서 연 지도', () => {
  const friendMap = (id: string): PartyMap => ({ ...map(id), mine: false, role: 'VIEWER', ownerNickname: '친구', friendAccess: 'VIEWER' });

  it('openMap 은 목록에 없는 지도를 더하고 연다', async () => {
    api.listMyMaps.mockResolvedValue([map('a')]);
    api.listPins.mockResolvedValue([pin('p1', 'f')]);
    await useMapStore.getState().loadMaps();

    await useMapStore.getState().openMap(friendMap('f'));

    expect(useMapStore.getState().maps.map((m) => m.id)).toEqual(['a', 'f']);
    expect(useMapStore.getState().selectedMapId).toBe('f');
    expect(api.listPins).toHaveBeenLastCalledWith('f', expect.anything());
  });

  it('이미 목록에 있는 지도는 다시 더하지 않고 연다', async () => {
    api.listMyMaps.mockResolvedValue([map('a'), map('b')]);
    api.listPins.mockResolvedValue([]);
    await useMapStore.getState().loadMaps();

    await useMapStore.getState().openMap(map('b'));

    expect(useMapStore.getState().maps).toHaveLength(2);
    expect(useMapStore.getState().selectedMapId).toBe('b');
  });

  it('새로고침해도 마지막으로 보던 친구 지도를 아직 볼 수 있으면 이어서 연다', async () => {
    api.listMyMaps.mockResolvedValue([map('a')]);
    api.getMap.mockResolvedValue(friendMap('f'));
    api.listPins.mockResolvedValue([]);
    window.localStorage.setItem('party.selectedMapId', 'f');

    await useMapStore.getState().loadMaps();

    expect(api.getMap).toHaveBeenCalledWith('f');
    expect(useMapStore.getState().maps.map((m) => m.id)).toEqual(['a', 'f']);
    expect(useMapStore.getState().selectedMapId).toBe('f');
  });

  it('마지막으로 보던 지도를 더는 볼 수 없으면(친구를 끊음·공개 해제·삭제) 첫 지도로 돌아간다', async () => {
    api.listMyMaps.mockResolvedValue([map('a')]);
    api.getMap.mockRejectedValue(new Error('권한 없음'));
    api.listPins.mockResolvedValue([]);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    window.localStorage.setItem('party.selectedMapId', 'f');

    await useMapStore.getState().loadMaps();

    expect(useMapStore.getState().maps.map((m) => m.id)).toEqual(['a']);
    expect(useMapStore.getState().selectedMapId).toBe('a');
    expect(useMapStore.getState().error).toBeNull();
    expect(warn).toHaveBeenCalled();
  });

  it('내 목록에 있는 지도를 기억하고 있으면 따로 묻지 않는다', async () => {
    api.listMyMaps.mockResolvedValue([map('a'), map('b')]);
    api.listPins.mockResolvedValue([]);
    window.localStorage.setItem('party.selectedMapId', 'b');

    await useMapStore.getState().loadMaps();

    expect(api.getMap).not.toHaveBeenCalled();
  });
});

describe('applyMap', () => {
  it('서버가 돌려준 지도 정보(공개 범위 등)를 목록에 반영한다', async () => {
    api.listMyMaps.mockResolvedValue([map('a'), map('b')]);
    api.listPins.mockResolvedValue([]);
    await useMapStore.getState().loadMaps();

    useMapStore.getState().applyMap({ ...map('b'), friendAccess: 'EDITOR' });

    expect(useMapStore.getState().maps.find((m) => m.id === 'b')?.friendAccess).toBe('EDITOR');
    expect(useMapStore.getState().maps.find((m) => m.id === 'a')?.friendAccess).toBe('NONE');
  });
});

describe('loadMaps', () => {
  it('마지막으로 보던 지도를 열고, 없으면 첫 지도를 연다', async () => {
    api.listMyMaps.mockResolvedValue([map('a'), map('b')]);
    api.listPins.mockResolvedValue([pin('p1', 'b')]);
    window.localStorage.setItem('party.selectedMapId', 'b');

    await useMapStore.getState().loadMaps();

    expect(useMapStore.getState().selectedMapId).toBe('b');
    expect(useMapStore.getState().pins).toHaveLength(1);

    // 기억한 지도가 없어졌거나 더는 볼 수 없으면(서버가 거절) 첫 지도를 연다
    api.getMap.mockRejectedValue(new Error('접근 불가'));
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
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

describe('사적 메모', () => {
  beforeEach(async () => {
    api.listMyMaps.mockResolvedValue([map('a')]);
    api.listPins.mockResolvedValue([pin('p1', 'a')]);
    records.listPrivateNotes.mockResolvedValue([{ pinId: 'p1', body: '불친절', updatedAt: '' }]);
    await useMapStore.getState().loadMaps();
  });

  it('지도를 열 때 내 메모를 핀 ID 로 묶어 가져온다', () => {
    expect(useMapStore.getState().privateNotes).toEqual({ p1: '불친절' });
  });

  it('저장하면 덮어쓰고 삭제하면 지운다', async () => {
    records.savePrivateNote.mockResolvedValue({ pinId: 'p1', body: '괜찮았음', updatedAt: '' });
    records.deletePrivateNote.mockResolvedValue();

    await useMapStore.getState().savePrivateNote('p1', '괜찮았음');
    expect(useMapStore.getState().privateNotes).toEqual({ p1: '괜찮았음' });

    await useMapStore.getState().deletePrivateNote('p1');
    expect(useMapStore.getState().privateNotes).toEqual({});
  });

  it('메모를 못 불러와도 핀은 보여 준다', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    records.listPrivateNotes.mockRejectedValue(new Error('서버 오류'));

    await useMapStore.getState().selectMap('a');

    expect(useMapStore.getState().pins).toHaveLength(1);
    expect(useMapStore.getState().privateNotes).toEqual({});
    expect(useMapStore.getState().error).toBeNull();
  });

  it('지도를 바꾸면 이전 지도의 메모는 비운다', async () => {
    api.listPins.mockResolvedValue([]);
    records.listPrivateNotes.mockResolvedValue([]);

    await useMapStore.getState().selectMap('b');

    expect(useMapStore.getState().privateNotes).toEqual({});
  });
});

describe('reloadPins', () => {
  it('선택과 필터를 유지한 채 핀을 다시 불러오고 지도의 핀 개수를 맞춘다', async () => {
    api.listMyMaps.mockResolvedValue([map('a', 1)]);
    api.listPins.mockResolvedValueOnce([pin('p1', 'a')]);
    await useMapStore.getState().loadMaps();
    useMapStore.getState().setStatusFilter('VISITED');
    api.listPins.mockResolvedValueOnce([pin('p1', 'a', { status: 'VISITED', visitCount: 1 }), pin('p2', 'a')]);

    await useMapStore.getState().reloadPins();

    expect(useMapStore.getState().pins.map((p) => p.id)).toEqual(['p1', 'p2']);
    expect(useMapStore.getState().pins[0]).toMatchObject({ status: 'VISITED', visitCount: 1 });
    expect(useMapStore.getState().maps[0].pinCount).toBe(2);
    expect(useMapStore.getState().statusFilter).toBe('VISITED');
  });

  it('다시 불러오지 못해도 기존 목록을 그대로 두고 오류로 올리지 않는다', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    api.listMyMaps.mockResolvedValue([map('a', 1)]);
    api.listPins.mockResolvedValueOnce([pin('p1', 'a')]);
    await useMapStore.getState().loadMaps();
    api.listPins.mockRejectedValueOnce(new Error('끊김'));

    await useMapStore.getState().reloadPins();

    expect(useMapStore.getState().pins).toHaveLength(1);
    expect(useMapStore.getState().error).toBeNull();
  });
});
