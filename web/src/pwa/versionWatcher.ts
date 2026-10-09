export interface VersionWatcherOptions {
  /** 서비스 워커 파일 주소(/party/sw.js) */
  scriptUrl: string;
  scope: string;
  /** 이미 떠 있던 화면에서 새 버전이 적용됐을 때 한 번 부른다 */
  onNewVersion: () => void;
  /** 화면이 보이는 동안 새 버전을 확인하는 간격 */
  checkEveryMs: number;
  /** 연달아 확인하지 않는 최소 간격(화면 복귀가 잦아도 서버를 두드리지 않는다) */
  minGapMs: number;
  now?: () => number;
}

/**
 * 서비스 워커를 등록하고 새 버전을 알아챈다.
 *
 * 서비스 워커는 새 버전이 받아지면 바로 활성화되지만(skipWaiting·clientsClaim), 이미 떠 있는 화면은 옛 코드 그대로다.
 * 홈 화면에 설치한 앱은 몇 날씩 다시 로드되지 않아서, 기본 등록(페이지 로드 때 한 번)만으로는 새 버전을 오래 못 받는다.
 * 그래서 ① 화면으로 돌아올 때와 일정 간격마다 서비스 워커 파일을 다시 확인하고, ② 이미 서비스 워커의 통제를 받던 화면에서
 * 통제권이 바뀌면(= 새 버전 활성화) 호출자에게 알린다. 처음 설치되어 통제권을 얻는 경우는 새 버전이 아니므로 무시한다.
 *
 * 서비스 워커를 쓸 수 없는 환경이면 null.
 */
export function watchForNewVersion(options: VersionWatcherOptions): { stop: () => void } | null {
  if (!('serviceWorker' in navigator)) {
    return null;
  }
  const container = navigator.serviceWorker;
  const now = options.now ?? Date.now;
  const hadController = container.controller !== null;
  let registration: ServiceWorkerRegistration | null = null;
  let lastCheckAt = now();
  let notified = false;

  const onControllerChange = () => {
    if (hadController && !notified) {
      notified = true;
      options.onNewVersion();
    }
  };

  const checkNow = () => {
    if (registration === null || document.visibilityState !== 'visible' || now() - lastCheckAt < options.minGapMs) {
      return;
    }
    lastCheckAt = now();
    registration.update().catch((failure: unknown) => {
      // 확인에 실패해도(오프라인 등) 앱은 그대로 쓴다. 다음 기회에 다시 확인한다.
      console.warn('Service worker update check failed', failure);
    });
  };

  const register = () => {
    container
      .register(options.scriptUrl, { scope: options.scope })
      .then((created) => {
        registration = created;
      })
      .catch((failure: unknown) => {
        console.error('Service worker registration failed', failure);
      });
  };

  container.addEventListener('controllerchange', onControllerChange);
  document.addEventListener('visibilitychange', checkNow);
  const timer = window.setInterval(checkNow, options.checkEveryMs);
  if (document.readyState === 'complete') {
    register();
  } else {
    window.addEventListener('load', register, { once: true });
  }

  return {
    stop: () => {
      container.removeEventListener('controllerchange', onControllerChange);
      document.removeEventListener('visibilitychange', checkNow);
      window.removeEventListener('load', register);
      window.clearInterval(timer);
    },
  };
}
