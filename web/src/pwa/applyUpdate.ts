export interface ApplyWhenSafeOptions {
  /** 지금 새로 불러와도 사용자가 잃을 것이 없는지(열린 시트·입력 중인 글이 없는지) */
  isSafe: () => boolean;
  /** 현재 주소를 다시 불러와 새 버전의 화면으로 바꾼다 */
  apply: () => void;
}

/**
 * 새 버전이 적용되어 있을 때, 사용자 눈에 띄지 않는 순간에 조용히 새 화면으로 바꾸는 규칙을 만든다(배너를 띄우지 않는다).
 *  - 화면이 가려져 있을 때 새 버전이 들어오면 아무도 보지 않으니 바로 바꾼다.
 *  - 화면이 보이는 중이면 입력 중인 글이나 열어 둔 시트를 날리지 않도록 기다렸다가, 앱을 떠났다 돌아오는 순간에 안전할 때 바꾼다.
 *  - 그때도 안전하지 않으면(시트가 열려 있음 등) 다음에 돌아올 때 다시 시도한다.
 * 돌려주는 함수는 "새 버전이 적용됨" 을 알리는 용도이고, stop 은 정리용이다.
 */
export function createUpdateApplier(options: ApplyWhenSafeOptions): { onNewVersion: () => void; stop: () => void } {
  let pending = false;

  const tryApply = () => {
    if (pending && options.isSafe()) {
      pending = false;
      options.apply();
    }
  };

  const onVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      tryApply();
    }
  };
  document.addEventListener('visibilitychange', onVisibilityChange);

  return {
    onNewVersion: () => {
      pending = true;
      if (document.visibilityState === 'hidden') {
        pending = false;
        options.apply();
      }
    },
    stop: () => document.removeEventListener('visibilitychange', onVisibilityChange),
  };
}

/** 열린 시트(role=dialog)가 없고 글자를 입력하는 칸에 커서가 없을 때만 안전하다. */
export function isSafeToReload(): boolean {
  if (document.querySelector('[role="dialog"]') !== null) {
    return false;
  }
  const active = document.activeElement;
  return !(active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement || (active instanceof HTMLElement && active.isContentEditable));
}

/** 현재 주소를 다시 불러온다. 상태 불일치를 새로고침으로 덮으려는 것이 아니라 앱 코드(번들) 자체를 새 버전으로 교체하는 것이다. */
export function reloadToNewVersion(): void {
  window.location.replace(window.location.href);
}
