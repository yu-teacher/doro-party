import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createUpdateApplier, isSafeToReload } from './applyUpdate';

const visibility = (state: 'visible' | 'hidden') => Object.defineProperty(document, 'visibilityState', { configurable: true, value: state });
const changeVisibility = (state: 'visible' | 'hidden') => {
  visibility(state);
  document.dispatchEvent(new Event('visibilitychange'));
};

let stop: () => void = () => undefined;
beforeEach(() => visibility('visible'));
afterEach(() => {
  stop();
  document.body.innerHTML = '';
  visibility('visible');
});

function setup(safe: boolean) {
  const apply = vi.fn();
  const isSafe = vi.fn(() => safe);
  const applier = createUpdateApplier({ isSafe, apply });
  stop = applier.stop;
  return { apply, isSafe, applier };
}

describe('새 버전을 조용히 적용하기', () => {
  it('화면이 보이는 중에 새 버전이 오면 바로 바꾸지 않는다(쓰던 화면을 날리지 않는다)', () => {
    const { apply, applier } = setup(true);
    applier.onNewVersion();
    expect(apply).not.toHaveBeenCalled();
  });

  it('앱을 떠났다 돌아오는 순간에 안전하면 바꾼다', () => {
    const { apply, applier } = setup(true);
    applier.onNewVersion();
    changeVisibility('hidden');
    changeVisibility('visible');
    expect(apply).toHaveBeenCalledTimes(1);
  });

  it('돌아와도 안전하지 않으면(시트가 열려 있음 등) 바꾸지 않고, 다음에 안전할 때 바꾼다', () => {
    const apply = vi.fn();
    let safe = false;
    const applier = createUpdateApplier({ isSafe: () => safe, apply });
    stop = applier.stop;
    applier.onNewVersion();

    changeVisibility('visible');
    expect(apply).not.toHaveBeenCalled();

    safe = true;
    changeVisibility('visible');
    expect(apply).toHaveBeenCalledTimes(1);
  });

  it('화면이 가려져 있을 때 새 버전이 오면 아무도 보지 않으니 바로 바꾼다', () => {
    const { apply, applier } = setup(false);
    visibility('hidden');
    applier.onNewVersion();
    expect(apply).toHaveBeenCalledTimes(1);
  });

  it('한 번 바꾼 뒤에는 화면을 오가도 다시 바꾸지 않는다', () => {
    const { apply, applier } = setup(true);
    applier.onNewVersion();
    changeVisibility('visible');
    changeVisibility('visible');
    expect(apply).toHaveBeenCalledTimes(1);
  });

  it('새 버전이 없으면 화면을 오가도 아무것도 하지 않는다', () => {
    const { apply } = setup(true);
    changeVisibility('hidden');
    changeVisibility('visible');
    expect(apply).not.toHaveBeenCalled();
  });
});

describe('지금 새로 불러와도 안전한가', () => {
  it('아무것도 열려 있지 않으면 안전하다', () => {
    expect(isSafeToReload()).toBe(true);
  });

  it('열린 시트가 있으면 안전하지 않다', () => {
    document.body.innerHTML = '<div role="dialog"></div>';
    expect(isSafeToReload()).toBe(false);
  });

  it('글자를 입력하는 칸에 커서가 있으면 안전하지 않다', () => {
    document.body.innerHTML = '<textarea id="t"></textarea>';
    document.getElementById('t')?.focus();
    expect(isSafeToReload()).toBe(false);
  });
});
