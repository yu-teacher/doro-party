import axios from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as socialApi from '../api/socialApi';
import type { Pin, RecommendedPlace } from '../api/types';
import { readRememberedOverlayMaps, useOverlayStore } from './overlayStore';

vi.mock('../api/socialApi');
const api = vi.mocked(socialApi);

function pin(id: string, mapId: string, createdBy: string): Pin {
  return {
    id, mapId, createdBy, authorNickname: createdBy, authorColor: '#E4572E', lat: 37.5, lng: 127, name: `핀 ${id}`, sharedMemo: null,
    status: 'WISH', rating: null, revisitIntent: null, tags: [], visitCount: 0, lastVisitedOn: null, photoCount: 0, createdAt: '', updatedAt: '',
  };
}

beforeEach(() => {
  window.localStorage.clear();
  useOverlayStore.getState().close();
  vi.resetAllMocks();
  api.getRecommendations.mockResolvedValue({ mapIds: [], places: [] });
});

describe('겹쳐보기 불러오기', () => {
  it('열면 고른 지도의 핀을 불러오고, 선택을 기억한다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a', 'b'], pins: [pin('1', 'a', 'u1'), pin('2', 'b', 'u2')] });

    await useOverlayStore.getState().open(['a', 'b']);

    expect(useOverlayStore.getState()).toMatchObject({ active: true, loading: false, error: null, loadCount: 1 });
    expect(useOverlayStore.getState().pins).toHaveLength(2);
    expect(readRememberedOverlayMaps()).toEqual(['a', 'b']);
  });

  it('지도를 바꿔 고르면 늦게 도착한 이전 응답은 무시한다', async () => {
    let resolveFirst: (value: { mapIds: string[]; pins: Pin[] }) => void = () => undefined;
    api.getOverlay.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));
    api.getOverlay.mockResolvedValueOnce({ mapIds: ['b'], pins: [pin('second', 'b', 'u2')] });

    const first = useOverlayStore.getState().open(['a']);
    await useOverlayStore.getState().setMaps(['b']);
    resolveFirst({ mapIds: ['a'], pins: [pin('stale', 'a', 'u1')] });
    await first;

    expect(useOverlayStore.getState().pins.map((p) => p.id)).toEqual(['second']);
  });

  it('요청이 취소된 오류는 화면에 오류로 보여 주지 않는다', async () => {
    api.getOverlay.mockRejectedValue(new axios.CanceledError('canceled'));

    await useOverlayStore.getState().open(['a']);

    expect(useOverlayStore.getState().error).toBeNull();
  });

  it('불러오지 못하면 서버의 안내 문구를 남긴다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    api.getOverlay.mockRejectedValue(new Error('한 번에 겹쳐볼 수 있는 지도는 최대 20개입니다.'));

    await useOverlayStore.getState().open(['a']);

    expect(useOverlayStore.getState()).toMatchObject({ loading: false, error: '한 번에 겹쳐볼 수 있는 지도는 최대 20개입니다.' });
  });

  it('지도를 하나도 고르지 않으면 요청하지 않고 빈 결과', async () => {
    await useOverlayStore.getState().open([]);

    expect(api.getOverlay).not.toHaveBeenCalled();
    expect(useOverlayStore.getState().pins).toEqual([]);
  });
});

describe('작성자 켜기·끄기', () => {
  it('작성자를 감췄다 다시 보이게 하고, 더는 없는 작성자는 새로 불러올 때 정리한다', async () => {
    api.getOverlay.mockResolvedValueOnce({ mapIds: ['a'], pins: [pin('1', 'a', 'u1'), pin('2', 'a', 'u2')] });
    await useOverlayStore.getState().open(['a']);

    useOverlayStore.getState().toggleAuthor('u2');
    expect(useOverlayStore.getState().hiddenAuthors).toEqual(['u2']);
    useOverlayStore.getState().toggleAuthor('u2');
    expect(useOverlayStore.getState().hiddenAuthors).toEqual([]);

    useOverlayStore.getState().toggleAuthor('u2');
    api.getOverlay.mockResolvedValueOnce({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    await useOverlayStore.getState().setMaps(['a']);
    expect(useOverlayStore.getState().hiddenAuthors).toEqual([]);
  });

  it('닫으면 모두 초기화된다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    await useOverlayStore.getState().open(['a']);

    useOverlayStore.getState().close();

    expect(useOverlayStore.getState()).toMatchObject({ active: false, pins: [], mapIds: [], hiddenAuthors: [] });
  });
});

describe('기억한 지도', () => {
  it('저장소에 깨진 값이 있어도 빈 목록으로 시작한다', () => {
    window.localStorage.setItem('party.overlayMapIds', '{not json');
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(readRememberedOverlayMaps()).toEqual([]);

    window.localStorage.setItem('party.overlayMapIds', JSON.stringify(['a', 3, null, 'b']));
    expect(readRememberedOverlayMaps()).toEqual(['a', 'b']);
  });
});

function place(rank: number, name: string): RecommendedPlace {
  return {
    rank, lat: 37.5, lng: 127, name, otherNames: [], people: 2, pinCount: 2, score: 30,
    breakdown: {
      people: 2, peoplePoints: 20, wishCount: 2, wishPoints: 8, visitedCount: 0, visitedPoints: 0, againCount: 0, againPoints: 0,
      onceCount: 0, oncePoints: 0, ratedCount: 0, ratingAverage: null, ratingPoints: 0,
    },
    authors: [], pinIds: ['p1', 'p2'], mapIds: ['a'],
  };
}

describe('추천', () => {
  it('겹쳐보기를 열면 고른 지도로 추천을 계산해 둔다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    api.getRecommendations.mockResolvedValue({ mapIds: ['a'], places: [place(1, '연남 파스타')] });

    await useOverlayStore.getState().open(['a']);
    await vi.waitFor(() => expect(useOverlayStore.getState().recommendations).toHaveLength(1));

    expect(api.getRecommendations).toHaveBeenCalledWith(['a'], { excludeAuthors: [], minPeople: 1 }, expect.anything());
    expect(useOverlayStore.getState().recommendations[0].name).toBe('연남 파스타');
    expect(useOverlayStore.getState().recsLoading).toBe(false);
  });

  it('작성자를 감추면 그 사람을 빼고 다시 계산하고, 다시 보이게 하면 되돌린다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1'), pin('2', 'a', 'u2')] });
    await useOverlayStore.getState().open(['a']);
    await vi.waitFor(() => expect(api.getRecommendations).toHaveBeenCalledTimes(1));

    useOverlayStore.getState().toggleAuthor('u2');
    await vi.waitFor(() => expect(api.getRecommendations).toHaveBeenCalledTimes(2));
    expect(api.getRecommendations).toHaveBeenLastCalledWith(['a'], { excludeAuthors: ['u2'], minPeople: 1 }, expect.anything());

    useOverlayStore.getState().toggleAuthor('u2');
    await vi.waitFor(() => expect(api.getRecommendations).toHaveBeenCalledTimes(3));
    expect(api.getRecommendations).toHaveBeenLastCalledWith(['a'], { excludeAuthors: [], minPeople: 1 }, expect.anything());
  });

  it('최소 인원을 바꾸면 그 값으로 다시 계산한다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    await useOverlayStore.getState().open(['a']);

    await useOverlayStore.getState().setMinPeople(2);

    expect(useOverlayStore.getState().minPeople).toBe(2);
    expect(api.getRecommendations).toHaveBeenLastCalledWith(['a'], { excludeAuthors: [], minPeople: 2 }, expect.anything());
  });

  it('조건을 빠르게 바꿀 때 먼저 보낸 요청의 늦은 응답은 무시한다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    await useOverlayStore.getState().open(['a']);
    await vi.waitFor(() => expect(api.getRecommendations).toHaveBeenCalled());
    let resolveSlow: (value: { mapIds: string[]; places: RecommendedPlace[] }) => void = () => undefined;
    api.getRecommendations.mockImplementationOnce(() => new Promise((resolve) => { resolveSlow = resolve; }));
    api.getRecommendations.mockResolvedValueOnce({ mapIds: ['a'], places: [place(1, '최신')] });

    const slow = useOverlayStore.getState().setMinPeople(2);
    await useOverlayStore.getState().setMinPeople(3);
    resolveSlow({ mapIds: ['a'], places: [place(1, '오래된 응답')] });
    await slow;

    expect(useOverlayStore.getState().recommendations.map((p) => p.name)).toEqual(['최신']);
  });

  it('추천을 못 불러와도 겹친 핀은 그대로 보이고, 서버 안내 문구를 남긴다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    api.getRecommendations.mockRejectedValue(new Error('추천 서버 오류'));

    await useOverlayStore.getState().open(['a']);
    await vi.waitFor(() => expect(useOverlayStore.getState().recsError).toBe('추천 서버 오류'));

    expect(useOverlayStore.getState().pins).toHaveLength(1);
    expect(useOverlayStore.getState().error).toBeNull();
  });

  it('히트맵은 켜고 끌 수 있고, 닫으면 추천과 함께 초기화된다', async () => {
    api.getOverlay.mockResolvedValue({ mapIds: ['a'], pins: [pin('1', 'a', 'u1')] });
    api.getRecommendations.mockResolvedValue({ mapIds: ['a'], places: [place(1, '장소')] });
    await useOverlayStore.getState().open(['a']);
    await vi.waitFor(() => expect(useOverlayStore.getState().recommendations).toHaveLength(1));

    useOverlayStore.getState().toggleHeatmap();
    expect(useOverlayStore.getState().heatmap).toBe(true);
    useOverlayStore.getState().close();

    expect(useOverlayStore.getState()).toMatchObject({ heatmap: false, recommendations: [], minPeople: 1, active: false });
  });
});
