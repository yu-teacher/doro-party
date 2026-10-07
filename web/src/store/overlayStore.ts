import axios from 'axios';
import { create } from 'zustand';
import * as socialApi from '../api/socialApi';
import type { Pin, RecommendedPlace } from '../api/types';

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
  /** 화면에서 잠시 감춘 작성자(userId). 추천 점수에서도 이 사람들의 핀을 뺀다. */
  hiddenAuthors: string[];
  /** 추천 장소(점수 순). 겹친 지도·감춘 작성자·최소 인원이 바뀔 때마다 다시 계산한다. */
  recommendations: RecommendedPlace[];
  recsLoading: boolean;
  recsError: string | null;
  /** 이 인원 이상이 찍은 장소만 추천한다 */
  minPeople: number;
  /** 추천 장소를 지도 위에 열기(히트맵)로 보여 줄지 */
  heatmap: boolean;

  /** 겹쳐보기를 켜고 이 지도들을 겹친다. */
  open: (mapIds: string[]) => Promise<void>;
  /** 겹칠 지도를 바꾼다. */
  setMaps: (mapIds: string[]) => Promise<void>;
  toggleAuthor: (userId: string) => void;
  setMinPeople: (minPeople: number) => Promise<void>;
  toggleHeatmap: () => void;
  close: () => void;
}

/** 지도를 빠르게 바꿔 고를 때 먼저 보낸 요청의 늦은 응답이 화면을 덮어쓰지 않도록 이전 요청을 취소한다. */
let request: AbortController | null = null;
let recsRequest: AbortController | null = null;

const INITIAL = {
  active: false,
  mapIds: [] as string[],
  pins: [] as Pin[],
  loading: false,
  error: null as string | null,
  hiddenAuthors: [] as string[],
  recommendations: [] as RecommendedPlace[],
  recsLoading: false,
  recsError: null as string | null,
  minPeople: 1,
  heatmap: false,
};

export const useOverlayStore = create<OverlayState>((set, get) => {
  /** 지금 겹친 지도·감춘 작성자·최소 인원으로 추천을 다시 계산한다. 이전 요청의 늦은 응답은 무시한다. */
  const refreshRecommendations = async (): Promise<void> => {
    recsRequest?.abort();
    const { mapIds, hiddenAuthors, minPeople } = get();
    if (!get().active || mapIds.length === 0) {
      set({ recommendations: [], recsLoading: false, recsError: null });
      return;
    }
    const current = new AbortController();
    recsRequest = current;
    set({ recsLoading: true, recsError: null });
    try {
      const result = await socialApi.getRecommendations(mapIds, { excludeAuthors: hiddenAuthors, minPeople }, current.signal);
      if (recsRequest === current) {
        set({ recommendations: result.places, recsLoading: false });
      }
    } catch (error) {
      if (axios.isCancel(error)) {
        return;
      }
      console.error('Failed to load recommendations', error);
      if (recsRequest === current) {
        set({ recsLoading: false, recsError: error instanceof Error ? error.message : '추천을 불러오지 못했어요.' });
      }
    }
  };

  return {
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
      void refreshRecommendations();
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
        void refreshRecommendations();
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

  toggleAuthor: (userId) => {
    set((state) => ({
      hiddenAuthors: state.hiddenAuthors.includes(userId) ? state.hiddenAuthors.filter((id) => id !== userId) : [...state.hiddenAuthors, userId],
    }));
    void refreshRecommendations();
  },

  setMinPeople: async (minPeople) => {
    set({ minPeople });
    await refreshRecommendations();
  },

  toggleHeatmap: () => set((state) => ({ heatmap: !state.heatmap })),

  close: () => {
    request?.abort();
    recsRequest?.abort();
    set({ ...INITIAL });
  },
  };
});
