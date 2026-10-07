import axios from 'axios';
import { create } from 'zustand';
import * as mapsApi from '../api/mapsApi';
import type { MapInput, PartyMap, Pin, PinInput, PinStatus } from '../api/types';
import { readSelectedMapId, writeSelectedMapId } from '../utils/selectedMapStorage';

export type StatusFilter = PinStatus | 'ALL';

interface MapState {
  maps: PartyMap[];
  mapsLoaded: boolean;
  selectedMapId: string | null;
  pins: Pin[];
  pinsLoading: boolean;
  /** 목록을 불러오지 못했을 때의 안내 문구 */
  error: string | null;
  statusFilter: StatusFilter;
  tagFilter: string | null;

  /** 내 지도 목록을 불러오고, 마지막으로 보던 지도(없으면 첫 지도)를 연다. */
  loadMaps: () => Promise<void>;
  selectMap: (mapId: string | null) => Promise<void>;
  createMap: (input: MapInput) => Promise<PartyMap>;
  updateMap: (mapId: string, input: MapInput) => Promise<void>;
  removeMap: (mapId: string) => Promise<void>;
  addPin: (input: PinInput) => Promise<Pin>;
  editPin: (pinId: string, input: PinInput) => Promise<Pin>;
  removePin: (pinId: string) => Promise<void>;
  setStatusFilter: (filter: StatusFilter) => void;
  setTagFilter: (tag: string | null) => void;
  /** 로그아웃 등으로 내 데이터를 화면에서 비운다. */
  reset: () => void;
}

const INITIAL = {
  maps: [] as PartyMap[],
  mapsLoaded: false,
  selectedMapId: null as string | null,
  pins: [] as Pin[],
  pinsLoading: false,
  error: null as string | null,
  statusFilter: 'ALL' as StatusFilter,
  tagFilter: null as string | null,
};

/** 지도를 빠르게 바꿀 때 먼저 보낸 핀 요청의 늦은 응답이 화면을 덮어쓰지 않도록 이전 요청을 취소한다. */
let pinsRequest: AbortController | null = null;

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '요청 처리 중 오류가 발생했습니다.';
}

function withPinCount(maps: PartyMap[], mapId: string, delta: number): PartyMap[] {
  return maps.map((map) => (map.id === mapId ? { ...map, pinCount: Math.max(0, map.pinCount + delta) } : map));
}

export const useMapStore = create<MapState>((set, get) => ({
  ...INITIAL,

  loadMaps: async () => {
    try {
      const maps = await mapsApi.listMyMaps();
      const remembered = readSelectedMapId();
      const selected = maps.find((map) => map.id === remembered)?.id ?? maps[0]?.id ?? null;
      set({ maps, mapsLoaded: true, error: null });
      await get().selectMap(selected);
    } catch (error) {
      console.error('Failed to load maps', error);
      set({ mapsLoaded: true, error: messageOf(error) });
    }
  },

  selectMap: async (mapId) => {
    pinsRequest?.abort();
    writeSelectedMapId(mapId);
    set({ selectedMapId: mapId, pins: [], statusFilter: 'ALL', tagFilter: null, pinsLoading: mapId !== null });
    if (mapId === null) {
      return;
    }
    const request = new AbortController();
    pinsRequest = request;
    try {
      const pins = await mapsApi.listPins(mapId, request.signal);
      if (get().selectedMapId === mapId) {
        set({ pins, pinsLoading: false, error: null });
      }
    } catch (error) {
      if (axios.isCancel(error)) {
        return;
      }
      console.error('Failed to load pins', error);
      if (get().selectedMapId === mapId) {
        set({ pinsLoading: false, error: messageOf(error) });
      }
    }
  },

  createMap: async (input) => {
    const created = await mapsApi.createMap(input);
    set((state) => ({ maps: [created, ...state.maps] }));
    await get().selectMap(created.id);
    return created;
  },

  updateMap: async (mapId, input) => {
    const updated = await mapsApi.updateMap(mapId, input);
    set((state) => ({ maps: state.maps.map((map) => (map.id === mapId ? updated : map)) }));
  },

  removeMap: async (mapId) => {
    await mapsApi.deleteMap(mapId);
    const remaining = get().maps.filter((map) => map.id !== mapId);
    set({ maps: remaining });
    if (get().selectedMapId === mapId) {
      await get().selectMap(remaining[0]?.id ?? null);
    }
  },

  addPin: async (input) => {
    const mapId = get().selectedMapId;
    if (mapId === null) {
      throw new Error('지도를 먼저 골라 주세요.');
    }
    const created = await mapsApi.createPin(mapId, input);
    if (get().selectedMapId === mapId) {
      set((state) => ({ pins: [...state.pins, created], maps: withPinCount(state.maps, mapId, 1) }));
    }
    return created;
  },

  editPin: async (pinId, input) => {
    const mapId = get().selectedMapId;
    if (mapId === null) {
      throw new Error('지도를 먼저 골라 주세요.');
    }
    const updated = await mapsApi.updatePin(mapId, pinId, input);
    set((state) => ({ pins: state.pins.map((pin) => (pin.id === pinId ? updated : pin)) }));
    return updated;
  },

  removePin: async (pinId) => {
    const mapId = get().selectedMapId;
    if (mapId === null) {
      throw new Error('지도를 먼저 골라 주세요.');
    }
    await mapsApi.deletePin(mapId, pinId);
    set((state) => ({ pins: state.pins.filter((pin) => pin.id !== pinId), maps: withPinCount(state.maps, mapId, -1) }));
  },

  setStatusFilter: (filter) => set({ statusFilter: filter }),
  setTagFilter: (tag) => set({ tagFilter: tag }),

  reset: () => {
    pinsRequest?.abort();
    set({ ...INITIAL });
  },
}));

/** 상태·태그 필터를 적용한 핀. */
export function filterPins(pins: Pin[], statusFilter: StatusFilter, tagFilter: string | null): Pin[] {
  return pins.filter(
    (pin) => (statusFilter === 'ALL' || pin.status === statusFilter) && (tagFilter === null || pin.tags.includes(tagFilter)),
  );
}
