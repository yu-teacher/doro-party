import axios from 'axios';
import { create } from 'zustand';
import * as socialApi from '../api/socialApi';
import type { Pin } from '../api/types';

const STORAGE_KEY = 'party.overlayMapIds';

/** 마지막으로 겹쳐본 지도들을 기억한다. 저장소를 쓸 수 없는 환경에서는 조용히 기억만 포기한다. */
export function readRememberedOverlayMaps(): string[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw === null ? [] : JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === 'string') : [];
  } catch (error) {
    console.warn('Could not read the remembered overlay maps', error);
    return [];
  }
}

function rememberOverlayMaps(mapIds: string[]): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(mapIds));
  } catch (error) {
    console.warn('Could not remember the overlay maps', error);
  }
}

interface OverlayState {
  active: boolean;
  /** 지금 겹쳐 보려고 고른 지도 */
  mapIds: string[];
  pins: Pin[];
  loading: boolean;
  error: string | null;
  /** 불러오기가 끝날 때마다 늘어난다. 지도가 핀이 모두 보이게 맞추는 신호로 쓴다. */
  loadCount: number;
  /** 화면에서 잠시 감춘 작성자(userId) */
  hiddenAuthors: string[];

  /** 겹쳐보기를 켜고 이 지도들을 겹친다. */
  open: (mapIds: string[]) => Promise<void>;
  /** 겹칠 지도를 바꾼다. */
  setMaps: (mapIds: string[]) => Promise<void>;
  toggleAuthor: (userId: string) => void;
  close: () => void;
}

/** 지도를 빠르게 바꿔 고를 때 먼저 보낸 요청의 늦은 응답이 화면을 덮어쓰지 않도록 이전 요청을 취소한다. */
let request: AbortController | null = null;

const INITIAL = {
  active: false,
  mapIds: [] as string[],
  pins: [] as Pin[],
  loading: false,
  error: null as string | null,
  hiddenAuthors: [] as string[],
};

export const useOverlayStore = create<OverlayState>((set, get) => ({
  ...INITIAL,
  loadCount: 0,

  open: async (mapIds) => {
    set({ active: true });
    await get().setMaps(mapIds);
  },

  setMaps: async (mapIds) => {
    request?.abort();
    rememberOverlayMaps(mapIds);
    set({ mapIds, loading: true, error: null });
    if (mapIds.length === 0) {
      set((state) => ({ pins: [], loading: false, loadCount: state.loadCount + 1 }));
      return;
    }
    const current = new AbortController();
    request = current;
    try {
      const result = await socialApi.getOverlay(mapIds, current.signal);
      if (get().mapIds === mapIds) {
        const authorIds = new Set(result.pins.map((pin) => pin.createdBy));
        set((state) => ({
          pins: result.pins,
          loading: false,
          loadCount: state.loadCount + 1,
          hiddenAuthors: state.hiddenAuthors.filter((id) => authorIds.has(id)),
        }));
      }
    } catch (error) {
      if (axios.isCancel(error)) {
        return;
      }
      console.error('Failed to load the overlay', error);
      if (get().mapIds === mapIds) {
        set({ loading: false, error: error instanceof Error ? error.message : '겹쳐보지 못했어요.' });
      }
    }
  },

  toggleAuthor: (userId) =>
    set((state) => ({
      hiddenAuthors: state.hiddenAuthors.includes(userId) ? state.hiddenAuthors.filter((id) => id !== userId) : [...state.hiddenAuthors, userId],
    })),

  close: () => {
    request?.abort();
    set({ ...INITIAL });
  },
}));
