import axios from 'axios';
import { create } from 'zustand';
import * as commentsApi from '../api/commentsApi';
import type { UnreadPin } from '../api/types';

/** 새 댓글이 달린 핀 목록(내가 꽂았거나 내가 댓글을 남긴 핀). 앱을 열 때·핀을 닫을 때·화면으로 돌아올 때 새로 받는다. */
interface CommentState {
  unread: UnreadPin[];
  /** 서버에서 한 번이라도 받아 왔는지(받기 전에는 "없음" 으로 보이지 않게) */
  loaded: boolean;
  loadUnread: () => Promise<void>;
  reset: () => void;
}

/** 가장 마지막에 시작한 요청의 응답만 반영한다(오래된 응답이 늦게 와도 덮어쓰지 않는다) */
let latestRequest = 0;

export const useCommentStore = create<CommentState>((set) => ({
  unread: [],
  loaded: false,
  loadUnread: async () => {
    const request = ++latestRequest;
    try {
      const unread = await commentsApi.listUnread();
      if (request === latestRequest) {
        set({ unread, loaded: true });
      }
    } catch (failure) {
      if (!axios.isCancel(failure)) {
        // 알림 표시는 부가 기능이라 화면 전체를 오류로 바꾸지 않고, 이전 값을 그대로 둔다
        console.warn('Failed to load unread comments', failure);
      }
    }
  },
  reset: () => {
    latestRequest += 1;
    set({ unread: [], loaded: false });
  },
}));

/** 읽지 않은 댓글의 총 개수 */
export function totalUnread(unread: UnreadPin[]): number {
  return unread.reduce((sum, item) => sum + item.unread, 0);
}
