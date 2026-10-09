import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as commentsApi from '../api/commentsApi';
import type { PinComment } from '../api/types';
import { COMMENT_REFRESH_MS } from '../config';
import { useCommentStore } from '../store/commentStore';
import { useMapStore } from '../store/mapStore';
import { usePinComments } from './usePinComments';
import type { PinComments } from './usePinComments';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
vi.mock('../api/commentsApi');
const api = vi.mocked(commentsApi);

const reloadPins = vi.fn().mockResolvedValue(undefined);
const loadUnread = vi.fn().mockResolvedValue(undefined);

function comment(id: string, createdAt: string): PinComment {
  return { id, pinId: 'p', userId: 'u', authorNickname: '친구', authorColor: '#14B8A6', body: id, createdAt, editedAt: null };
}

let latest: PinComments;
function Probe({ mapId, pinId }: { mapId: string; pinId: string }) {
  latest = usePinComments(mapId, pinId);
  return null;
}

let container: HTMLDivElement;
let root: Root;
beforeEach(() => {
  vi.resetAllMocks();
  reloadPins.mockResolvedValue(undefined);
  loadUnread.mockResolvedValue(undefined);
  useMapStore.setState({ reloadPins });
  useCommentStore.setState({ loadUnread });
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function mount(mapId = 'm', pinId = 'p') {
  await act(async () => {
    root.render(<Probe mapId={mapId} pinId={pinId} />);
  });
}

describe('핀 댓글 훅', () => {
  it('불러오면 화면에 본 마지막 댓글 시각까지를 읽은 것으로 알리고 새 댓글 표시를 새로 받는다', async () => {
    api.listComments.mockResolvedValue([comment('a', '2026-10-09T01:00:00Z'), comment('b', '2026-10-09T02:00:00Z')]);
    api.markCommentsRead.mockResolvedValue(undefined);

    await mount();

    expect(latest.comments).toHaveLength(2);
    expect(api.markCommentsRead).toHaveBeenCalledWith('m', 'p', '2026-10-09T02:00:00Z');
    expect(loadUnread).toHaveBeenCalled();
    expect(latest.loading).toBe(false);
  });

  it('댓글이 없으면 읽음 표시를 보내지 않는다', async () => {
    api.listComments.mockResolvedValue([]);
    await mount();
    expect(api.markCommentsRead).not.toHaveBeenCalled();
  });

  it('읽음 표시가 실패해도 댓글은 보여 주고 오류 문구를 띄우지 않는다', async () => {
    api.listComments.mockResolvedValue([comment('a', '2026-10-09T01:00:00Z')]);
    api.markCommentsRead.mockRejectedValue(new Error('boom'));

    await mount();

    expect(latest.comments).toHaveLength(1);
    expect(latest.error).toBeNull();
    expect(console.warn).toHaveBeenCalled();
  });

  it('댓글을 불러오지 못하면 안내 문구를 둔다', async () => {
    api.listComments.mockRejectedValue(new Error('서버 오류'));
    await mount();
    expect(latest.error).toBe('서버 오류');
    expect(latest.loading).toBe(false);
  });

  it('쓰면 목록 끝에 붙고 핀을 다시 불러오며, 지우면 빠지고 핀을 다시 불러온다', async () => {
    api.listComments.mockResolvedValue([]);
    await mount();
    api.addComment.mockResolvedValue(comment('new', '2026-10-09T03:00:00Z'));
    api.deleteComment.mockResolvedValue(undefined);

    await act(async () => latest.add('안녕'));
    expect(api.addComment).toHaveBeenCalledWith('m', 'p', '안녕');
    expect(latest.comments.map((c) => c.id)).toEqual(['new']);
    expect(reloadPins).toHaveBeenCalledTimes(1);

    await act(async () => latest.remove('new'));
    expect(latest.comments).toHaveLength(0);
    expect(reloadPins).toHaveBeenCalledTimes(2);
  });

  it('고치면 그 댓글만 서버가 돌려준 내용으로 바뀐다', async () => {
    api.listComments.mockResolvedValue([comment('a', '2026-10-09T01:00:00Z'), comment('b', '2026-10-09T02:00:00Z')]);
    api.markCommentsRead.mockResolvedValue(undefined);
    await mount();
    api.editComment.mockResolvedValue({ ...comment('b', '2026-10-09T02:00:00Z'), body: '수정', editedAt: '2026-10-09T04:00:00Z' });

    await act(async () => latest.edit('b', '수정'));

    expect(latest.comments.map((c) => c.body)).toEqual(['a', '수정']);
    expect(latest.comments[1].editedAt).not.toBeNull();
  });

  it('다른 핀으로 바꾸면 이전 핀의 댓글은 비우고 새로 불러온다', async () => {
    api.listComments.mockResolvedValueOnce([comment('a', '2026-10-09T01:00:00Z')]).mockResolvedValueOnce([]);
    api.markCommentsRead.mockResolvedValue(undefined);
    await mount('m', 'p1');
    expect(latest.comments).toHaveLength(1);

    await mount('m', 'p2');

    expect(api.listComments).toHaveBeenLastCalledWith('m', 'p2', expect.any(AbortSignal));
    expect(latest.comments).toHaveLength(0);
  });
});

describe('핀 댓글 자동 갱신', () => {
  const visibility = (state: 'visible' | 'hidden') => Object.defineProperty(document, 'visibilityState', { configurable: true, value: state });
  const tick = (ms: number) => act(async () => { vi.advanceTimersByTime(ms); });

  beforeEach(() => {
    vi.useFakeTimers();
    visibility('visible');
    api.markCommentsRead.mockResolvedValue(undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    visibility('visible');
  });

  it('핀을 열어 두는 동안 일정 간격으로 다시 불러와 다른 사람의 새 댓글이 보인다', async () => {
    api.listComments.mockResolvedValueOnce([comment('a', '2026-10-09T01:00:00Z')]);
    await mount();
    expect(latest.comments.map((c) => c.id)).toEqual(['a']);

    api.listComments.mockResolvedValueOnce([comment('a', '2026-10-09T01:00:00Z'), comment('b', '2026-10-09T02:00:00Z')]);
    await tick(COMMENT_REFRESH_MS);

    expect(latest.comments.map((c) => c.id)).toEqual(['a', 'b']);
    expect(api.markCommentsRead).toHaveBeenLastCalledWith('m', 'p', '2026-10-09T02:00:00Z');
  });

  it('새 댓글이 없으면 읽음 표시를 다시 보내지 않는다', async () => {
    api.listComments.mockResolvedValue([comment('a', '2026-10-09T01:00:00Z')]);
    await mount();
    expect(api.markCommentsRead).toHaveBeenCalledTimes(1);

    await tick(COMMENT_REFRESH_MS);
    await tick(COMMENT_REFRESH_MS);

    expect(api.listComments).toHaveBeenCalledTimes(3);
    expect(api.markCommentsRead).toHaveBeenCalledTimes(1);
  });

  it('화면이 가려져 있으면 불러오지 않고, 다시 보이면 바로 불러온다', async () => {
    api.listComments.mockResolvedValue([]);
    await mount();
    visibility('hidden');

    await tick(COMMENT_REFRESH_MS * 2);
    expect(api.listComments).toHaveBeenCalledTimes(1);

    visibility('visible');
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    expect(api.listComments).toHaveBeenCalledTimes(2);
  });

  it('조용히 갱신하다 실패하면 보이던 댓글과 오류 문구는 그대로 두고 기록만 남긴다', async () => {
    api.listComments.mockResolvedValueOnce([comment('a', '2026-10-09T01:00:00Z')]);
    await mount();
    api.listComments.mockRejectedValueOnce(new Error('network'));

    await tick(COMMENT_REFRESH_MS);

    expect(latest.comments).toHaveLength(1);
    expect(latest.error).toBeNull();
    expect(console.warn).toHaveBeenCalled();
  });

  it('방금 쓴 댓글이 그 전에 시작한 갱신 응답에 덮여 사라지지 않는다', async () => {
    api.listComments.mockResolvedValueOnce([]);
    await mount();

    let finishRefresh: (value: PinComment[]) => void = () => undefined;
    api.listComments.mockImplementationOnce(() => new Promise((resolve) => { finishRefresh = resolve; }));
    await tick(COMMENT_REFRESH_MS);
    api.addComment.mockResolvedValue(comment('mine', '2026-10-09T03:00:00Z'));
    await act(async () => latest.add('방금 쓴 글'));
    expect(latest.comments.map((c) => c.id)).toEqual(['mine']);

    await act(async () => finishRefresh([]));

    expect(latest.comments.map((c) => c.id)).toEqual(['mine']);
  });

  it('갱신이 끝나기 전에 또 시간이 되어도 겹쳐서 요청하지 않는다', async () => {
    api.listComments.mockResolvedValueOnce([]);
    await mount();
    let finishRefresh: (value: PinComment[]) => void = () => undefined;
    api.listComments.mockImplementationOnce(() => new Promise((resolve) => { finishRefresh = resolve; }));

    await tick(COMMENT_REFRESH_MS);
    await tick(COMMENT_REFRESH_MS);
    expect(api.listComments).toHaveBeenCalledTimes(2);

    await act(async () => finishRefresh([]));
  });

  it('핀을 닫으면(언마운트) 더는 불러오지 않는다', async () => {
    api.listComments.mockResolvedValue([]);
    await mount();
    act(() => root.unmount());
    root = createRoot(container);

    await tick(COMMENT_REFRESH_MS * 3);

    expect(api.listComments).toHaveBeenCalledTimes(1);
  });
});
