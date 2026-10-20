import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTER_BASENAME } from '../config';
import { toRouterPath } from '../push/pushClient';
import { useAuthStore } from '../store/authStore';
import { useCommentStore } from '../store/commentStore';
import { usePushStore } from '../store/pushStore';

/** 서비스 워커가 보내는 메시지: 새 알림이 왔다 / 알림을 눌러 이 화면으로 이동하라 */
const MESSAGE_PUSH_RECEIVED = 'party-push-received';
const MESSAGE_OPEN = 'party-open';

interface WorkerMessage {
  type?: string;
  url?: string;
}

/**
 * 화면에 보이지 않는 연결 부품.
 *  - 로그인하면 이 기기의 알림 구독을 서버와 맞추고, 로그아웃하면 상태를 비운다.
 *  - 알림이 오면 새 댓글 표시를 바로 갱신하고, 알림을 누르면 새로고침 없이 앱 안에서 해당 화면으로 이동한다.
 */
export default function PushSync() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      void usePushStore.getState().refresh();
    } else {
      usePushStore.getState().reset();
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!('serviceWorker' in navigator)) {
      return undefined;
    }
    const onMessage = (event: MessageEvent<WorkerMessage>) => {
      if (event.data?.type === MESSAGE_PUSH_RECEIVED) {
        void useCommentStore.getState().loadUnread();
      } else if (event.data?.type === MESSAGE_OPEN && typeof event.data.url === 'string') {
        const path = toRouterPath(event.data.url, window.location.origin, ROUTER_BASENAME);
        if (path !== null) {
          navigate(path);
          // 서비스 워커는 이 답이 없으면(이동 메시지를 처리하지 못하는 옛 버전 화면 등) 주소로 직접 이동시킨다
          event.ports[0]?.postMessage('handled');
        }
      }
    };
    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, [navigate]);

  return null;
}
