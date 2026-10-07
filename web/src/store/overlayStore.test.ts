import axios from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as socialApi from '../api/socialApi';
import type { Pin } from '../api/types';
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
