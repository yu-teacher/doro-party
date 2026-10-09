import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as commentsApi from '../api/commentsApi';
import type { UnreadPin } from '../api/types';
import { totalUnread, useCommentStore } from './commentStore';

vi.mock('../api/commentsApi');
const api = vi.mocked(commentsApi);

const item = (pinId: string, unread: number): UnreadPin => ({ mapId: 'm', pinId, pinName: `핀 ${pinId}`, unread });

beforeEach(() => {
  useCommentStore.getState().reset();
  vi.resetAllMocks();
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
});

describe('새 댓글 목록', () => {
  it('서버에서 받아 오고, 개수를 합친다', async () => {
    api.listUnread.mockResolvedValue([item('a', 2), item('b', 1)]);

    await useCommentStore.getState().loadUnread();

    expect(useCommentStore.getState().loaded).toBe(true);
    expect(totalUnread(useCommentStore.getState().unread)).toBe(3);
  });

  it('요청이 겹치면 가장 나중에 시작한 요청의 응답만 반영한다(늦게 온 이전 응답이 덮어쓰지 않는다)', async () => {
    let resolveFirst: (value: UnreadPin[]) => void = () => undefined;
    api.listUnread.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));
    api.listUnread.mockResolvedValueOnce([item('new', 1)]);

    const first = useCommentStore.getState().loadUnread();
    await useCommentStore.getState().loadUnread();
    resolveFirst([item('old', 5)]);
    await first;

    expect(useCommentStore.getState().unread.map((entry) => entry.pinId)).toEqual(['new']);
  });

  it('불러오지 못하면 이전 값을 그대로 두고 기록만 남긴다', async () => {
    api.listUnread.mockResolvedValueOnce([item('a', 1)]);
    await useCommentStore.getState().loadUnread();
    api.listUnread.mockRejectedValueOnce(new Error('network'));

    await useCommentStore.getState().loadUnread();

    expect(useCommentStore.getState().unread).toHaveLength(1);
    expect(console.warn).toHaveBeenCalled();
  });

  it('비우면(로그아웃) 진행 중이던 응답도 반영하지 않는다', async () => {
    let resolveFirst: (value: UnreadPin[]) => void = () => undefined;
    api.listUnread.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));

    const pending = useCommentStore.getState().loadUnread();
    useCommentStore.getState().reset();
    resolveFirst([item('a', 3)]);
    await pending;

    expect(useCommentStore.getState()).toMatchObject({ unread: [], loaded: false });
  });
});
