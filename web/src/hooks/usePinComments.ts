import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import * as commentsApi from '../api/commentsApi';
import type { PinComment } from '../api/types';
import { COMMENT_REFRESH_MS } from '../config';
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
 *
 * 핀을 열어 두는 동안 다른 사람의 댓글이 보이도록 화면이 보일 때 일정 간격(COMMENT_REFRESH_MS)으로, 그리고 화면으로 돌아올 때
 * 조용히 다시 불러온다(실패해도 이미 보이는 댓글은 그대로 두고 기록만 남긴다).
 */
export function usePinComments(mapId: string, pinId: string): PinComments {
  const reloadPins = useMapStore((state) => state.reloadPins);
  const loadUnread = useCommentStore((state) => state.loadUnread);
  const [comments, setComments] = useState<PinComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  /** 내가 쓰거나 고치거나 지울 때마다 올린다: 그 전에 시작한 불러오기 응답이 방금 한 변경을 덮어쓰지 않게 한다 */
  const mutations = useRef(0);
  const refreshing = useRef(false);
  const markedUpTo = useRef<string | null>(null);

  /** 가져온 목록을 반영하고, 새로 본 마지막 댓글까지를 읽은 것으로 알린다. 변경이 끼어들었으면 버린다. */
  const load = useCallback(
    async (signal: AbortSignal): Promise<boolean> => {
      const startedAt = mutations.current;
      const loaded = await commentsApi.listComments(mapId, pinId, signal);
      if (signal.aborted || mutations.current !== startedAt) {
        return false;
      }
      setComments(loaded);
      const last = loaded[loaded.length - 1];
      if (last && markedUpTo.current !== last.createdAt) {
        try {
          await commentsApi.markCommentsRead(mapId, pinId, last.createdAt);
          markedUpTo.current = last.createdAt;
          await loadUnread();
        } catch (failure) {
          // 읽음 표시는 부가 기능이다: 실패해도 댓글은 보여 주고, 다음 불러오기에서 다시 표시한다
          console.warn('Failed to mark comments read', failure);
        }
      }
      return true;
    },
    [mapId, pinId, loadUnread],
  );

  useEffect(() => {
    const request = new AbortController();
    setLoading(true);
    setError(null);
    setComments([]);
    markedUpTo.current = null;
    mutations.current = 0;
    load(request.signal)
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

    const refresh = () => {
      if (document.visibilityState !== 'visible' || refreshing.current) {
        return;
      }
      refreshing.current = true;
      load(request.signal)
        .catch((failure: unknown) => {
          if (!axios.isCancel(failure)) {
            console.warn('Failed to refresh comments', failure);
          }
        })
        .finally(() => {
          refreshing.current = false;
        });
    };
    const timer = window.setInterval(refresh, COMMENT_REFRESH_MS);
    document.addEventListener('visibilitychange', refresh);
    return () => {
      request.abort();
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', refresh);
      refreshing.current = false;
    };
  }, [load]);

  const add = useCallback(
    async (body: string) => {
      mutations.current += 1;
      const created = await commentsApi.addComment(mapId, pinId, body);
      mutations.current += 1;
      setComments((previous) => [...previous, created]);
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  const edit = useCallback(
    async (commentId: string, body: string) => {
      mutations.current += 1;
      const updated = await commentsApi.editComment(mapId, pinId, commentId, body);
      mutations.current += 1;
      setComments((previous) => previous.map((comment) => (comment.id === commentId ? updated : comment)));
    },
    [mapId, pinId],
  );

  const remove = useCallback(
    async (commentId: string) => {
      mutations.current += 1;
      await commentsApi.deleteComment(mapId, pinId, commentId);
      mutations.current += 1;
      setComments((previous) => previous.filter((comment) => comment.id !== commentId));
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  return { comments, loading, error, add, edit, remove };
}
