import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PushStatus } from '../store/pushStore';
import { usePushStore } from '../store/pushStore';
import NotificationSettingsHost from './NotificationSettingsSheet';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
const actions = {
  refresh: vi.fn().mockResolvedValue(undefined),
  enable: vi.fn().mockResolvedValue(undefined),
  disable: vi.fn().mockResolvedValue(undefined),
  setPreference: vi.fn().mockResolvedValue(undefined),
  sendTest: vi.fn().mockResolvedValue('시험 알림을 보냈어요. (1대)'),
};

beforeEach(() => {
  vi.clearAllMocks();
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function render(status: PushStatus, extra: Record<string, unknown> = {}) {
  usePushStore.setState({
    status, publicKey: 'KEY', preferences: null, busy: false, error: null, settingsOpen: true, ...actions, ...extra,
  });
  await act(async () => root.render(<NotificationSettingsHost />));
}

const button = (label: string) => [...container.querySelectorAll('button')].find((candidate) => candidate.textContent?.includes(label));

describe('알림 설정 시트', () => {
  it('열려 있지 않으면 아무것도 그리지 않는다', async () => {
    await render('off', { settingsOpen: false });
    expect(container.textContent).toBe('');
  });

  it('열릴 때 이 기기의 상태를 다시 확인한다', async () => {
    await render('off');
    expect(actions.refresh).toHaveBeenCalled();
  });

  it.each([
    ['unsupported', '지원하지 않아요'],
    ['needs-install', '홈 화면에 추가'],
    ['server-disabled', '아직 푸시 알림을 쓸 수 없어요'],
    ['denied', '알림이 막혀 있어요'],
  ] as const)('%s 이면 이유와 해결 방법을 알려 주고 켜기 버튼은 없다', async (status, text) => {
    await render(status);
    expect(container.textContent).toContain(text);
    expect(button('알림 받기')).toBeUndefined();
  });

  it('꺼져 있으면 켜기 버튼이 있고, 누르면 켠다', async () => {
    await render('off');
    await act(async () => button('이 기기에서 알림 받기')?.click());
    expect(actions.enable).toHaveBeenCalledTimes(1);
  });

  it('켜는 중에는 버튼이 막힌다', async () => {
    await render('off', { busy: true });
    expect((button('켜는 중') as HTMLButtonElement).disabled).toBe(true);
  });

  it('켜져 있으면 알림 종류를 체크박스로 고르고, 시험 알림·끄기 버튼이 있다', async () => {
    await render('on', { preferences: { comments: true, friendRequests: false, shares: true } });
    const boxes = [...container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];
    expect(boxes.map((box) => box.checked)).toEqual([true, false, true]);

    await act(async () => boxes[1].click());
    expect(actions.setPreference).toHaveBeenCalledWith('friendRequests', true);

    await act(async () => button('이 기기 알림 끄기')?.click());
    expect(actions.disable).toHaveBeenCalledTimes(1);
  });

  it('시험 알림을 보내면 결과 문구를 보여 준다', async () => {
    await render('on', { preferences: { comments: true, friendRequests: true, shares: true } });
    await act(async () => button('시험 알림 보내기')?.click());
    expect(container.textContent).toContain('시험 알림을 보냈어요. (1대)');
  });

  it('오류가 있으면 알려 준다', async () => {
    await render('off', { error: '알림 권한이 허용되지 않았어요.' });
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('권한이 허용되지 않았어요');
  });

  it('닫기를 누르면 닫힌다', async () => {
    await render('off');
    await act(async () => (container.querySelector('button[aria-label="닫기"]') as HTMLButtonElement).click());
    expect(usePushStore.getState().settingsOpen).toBe(false);
  });
});
