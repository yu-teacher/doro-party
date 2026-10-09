import axios from 'axios';
import { useCallback, useEffect, useState } from 'react';
import * as commentsApi from '../api/commentsApi';
import type { PinComment } from '../api/types';
import { useCommentStore } from '../store/commentStore';
import { useMapStore } from '../store/mapStore';

export interface PinComments {
  comments: PinComment[];
  loading: boolean;
  /** 댓글을 불러오지 못했을 때의 안내 문구 */
  error: string | null;
  add: (body: string) => Promise<void>;
  edit: (commentId: string, body: string) => Promise<void>;
  remove: (commentId: string) => Promise<void>;
}

/**
 * 열려 있는 핀의 댓글. 불러오면 화면에 본 마지막 댓글까지를 읽은 것으로 서버에 알리고(새 댓글 표시가 사라진다),
 * 쓰거나 지우면 핀 목록의 댓글 수도 맞춰야 하므로 핀을 다시 불러온다.
 */
export function usePinComments(mapId: string, pinId: string): PinComments {
  const reloadPins = useMapStore((state) => state.reloadPins);
  const loadUnread = useCommentStore((state) => state.loadUnread);
  const [comments, setComments] = useState<PinComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const request = new AbortController();
    setLoading(true);
    setError(null);
    setComments([]);
    commentsApi
      .listComments(mapId, pinId, request.signal)
      .then(async (loaded) => {
        setComments(loaded);
        const last = loaded[loaded.length - 1];
        if (last) {
          try {
            await commentsApi.markCommentsRead(mapId, pinId, last.createdAt);
            await loadUnread();
          } catch (failure) {
            // 읽음 표시는 부가 기능이다: 실패해도 댓글은 보여 주고, 다음에 열 때 다시 표시한다
            console.warn('Failed to mark comments read', failure);
          }
        }
      })
      .catch((failure: unknown) => {
        if (axios.isCancel(failure)) {
          return;
        }
        console.error('Failed to load comments', failure);
        setError(failure instanceof Error ? failure.message : '댓글을 불러오지 못했어요.');
      })
      .finally(() => {
        if (!request.signal.aborted) {
          setLoading(false);
        }
      });
    return () => request.abort();
  }, [mapId, pinId, loadUnread]);

  const add = useCallback(
    async (body: string) => {
      const created = await commentsApi.addComment(mapId, pinId, body);
      setComments((previous) => [...previous, created]);
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  const edit = useCallback(
    async (commentId: string, body: string) => {
      const updated = await commentsApi.editComment(mapId, pinId, commentId, body);
      setComments((previous) => previous.map((comment) => (comment.id === commentId ? updated : comment)));
    },
    [mapId, pinId],
  );

  const remove = useCallback(
    async (commentId: string) => {
      await commentsApi.deleteComment(mapId, pinId, commentId);
      setComments((previous) => previous.filter((comment) => comment.id !== commentId));
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  return { comments, loading, error, add, edit, remove };
}
