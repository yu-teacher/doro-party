import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { watchForNewVersion } from './versionWatcher';

interface FakeRegistration {
  update: ReturnType<typeof vi.fn>;
}

class FakeContainer extends EventTarget {
  controller: object | null;
  registration: FakeRegistration = { update: vi.fn().mockResolvedValue(undefined) };
  register = vi.fn().mockImplementation(() => Promise.resolve(this.registration));
  constructor(controlled: boolean) {
    super();
    this.controller = controlled ? {} : null;
  }
}

const original = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker');
let container: FakeContainer;
let clock = 0;
const visibility = (state: 'visible' | 'hidden') => Object.defineProperty(document, 'visibilityState', { configurable: true, value: state });

function install(controlled: boolean) {
  container = new FakeContainer(controlled);
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: container });
}

function start(onNewVersion = vi.fn()) {
  const handle = watchForNewVersion({ scriptUrl: '/party/sw.js', scope: '/party/', onNewVersion, checkEveryMs: 900_000, minGapMs: 60_000, now: () => clock });
  return { handle, onNewVersion };
}

async function settle() {
  await Promise.resolve();
  await Promise.resolve();
}

beforeEach(() => {
  vi.useFakeTimers();
  clock = 1_000_000;
  visibility('visible');
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  if (original) {
    Object.defineProperty(navigator, 'serviceWorker', original);
  } else {
    delete (navigator as { serviceWorker?: unknown }).serviceWorker;
  }
});

describe('새 버전 확인', () => {
  it('서비스 워커를 쓸 수 없으면 아무것도 하지 않는다', () => {
    delete (navigator as { serviceWorker?: unknown }).serviceWorker;
    expect(start().handle).toBeNull();
  });

  it('주소와 범위로 서비스 워커를 등록한다', async () => {
    install(true);
    start();
    await settle();
    expect(container.register).toHaveBeenCalledWith('/party/sw.js', { scope: '/party/' });
  });

  it('이미 통제받던 화면에서 통제권이 바뀌면(새 버전 활성화) 한 번만 알린다', async () => {
    install(true);
    const { onNewVersion } = start();
    await settle();

    container.dispatchEvent(new Event('controllerchange'));
    container.dispatchEvent(new Event('controllerchange'));

    expect(onNewVersion).toHaveBeenCalledTimes(1);
  });

  it('처음 설치돼 통제권을 얻는 경우는 새 버전이 아니므로 알리지 않는다', async () => {
    install(false);
    const { onNewVersion } = start();
    await settle();

    container.dispatchEvent(new Event('controllerchange'));

    expect(onNewVersion).not.toHaveBeenCalled();
  });

  it('화면으로 돌아오면 확인하지만, 최소 간격 안이면 다시 확인하지 않는다', async () => {
    install(true);
    start();
    await settle();

    clock += 30_000;
    document.dispatchEvent(new Event('visibilitychange'));
    expect(container.registration.update).not.toHaveBeenCalled();

    clock += 31_000;
    document.dispatchEvent(new Event('visibilitychange'));
    expect(container.registration.update).toHaveBeenCalledTimes(1);
  });

  it('화면이 가려져 있으면 확인하지 않고, 주기마다 보이는 동안에는 확인한다', async () => {
    install(true);
    start();
    await settle();

    visibility('hidden');
    clock += 1_000_000;
    vi.advanceTimersByTime(900_000);
    expect(container.registration.update).not.toHaveBeenCalled();

    visibility('visible');
    clock += 1_000_000;
    vi.advanceTimersByTime(900_000);
    expect(container.registration.update).toHaveBeenCalledTimes(1);
  });

  it('확인에 실패해도 던지지 않고 기록만 남긴다', async () => {
    install(true);
    container.registration.update.mockRejectedValue(new Error('offline'));
    start();
    await settle();

    clock += 120_000;
    document.dispatchEvent(new Event('visibilitychange'));
    await settle();

    expect(console.warn).toHaveBeenCalled();
  });

  it('멈추면 더는 확인하거나 알리지 않는다', async () => {
    install(true);
    const { handle, onNewVersion } = start();
    await settle();
    handle?.stop();

    clock += 1_000_000;
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(900_000);
    container.dispatchEvent(new Event('controllerchange'));

    expect(container.registration.update).not.toHaveBeenCalled();
    expect(onNewVersion).not.toHaveBeenCalled();
  });
});
