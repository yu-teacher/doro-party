import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';
import { UPDATE_CHECK_MIN_GAP_MS, UPDATE_CHECK_MS } from './config';
import { createUpdateApplier, isSafeToReload, reloadToNewVersion } from './pwa/applyUpdate';
import { watchForNewVersion } from './pwa/versionWatcher';
import { useAuthStore } from './store/authStore';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('#root 요소를 찾을 수 없습니다.');
}
const root = createRoot(rootElement);

// 서버에 로그인 상태를 먼저 확인한 뒤 화면을 그린다. (확인 전에 그리면 로그인 사용자가 잠깐 비로그인 화면을 보게 된다)
void useAuthStore.getState().loadSession().finally(() => {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});

// 서비스 워커 등록과 새 버전 확인(운영 빌드만: 개발 서버에는 서비스 워커가 없다).
// 새 버전은 배너 없이, 사용자가 앱을 떠났다 돌아오는 안전한 순간에 조용히 적용한다.
if (import.meta.env.PROD) {
  const updater = createUpdateApplier({ isSafe: isSafeToReload, apply: reloadToNewVersion });
  watchForNewVersion({
    scriptUrl: `${import.meta.env.BASE_URL}sw.js`,
    scope: import.meta.env.BASE_URL,
    onNewVersion: updater.onNewVersion,
    checkEveryMs: UPDATE_CHECK_MS,
    minGapMs: UPDATE_CHECK_MIN_GAP_MS,
  });
}
