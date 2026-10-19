import { create } from 'zustand';
import * as pushApi from '../api/pushApi';
import type { PushPreferences } from '../api/pushApi';
import * as pushClient from '../push/pushClient';

/**
 * 이 기기의 알림 상태.
 *  - loading: 확인 중
 *  - unsupported / needs-install: 이 브라우저에서는 켤 수 없다(아이폰은 홈 화면에 추가해야 한다)
 *  - server-disabled: 서버에 푸시 키가 설정돼 있지 않다
 *  - denied: 이 기기에서 알림 권한을 막아 둔 상태(브라우저 설정에서 풀어야 한다)
 *  - off / on: 켜기 전 / 켜진 상태
 */
export type PushStatus = 'loading' | 'unsupported' | 'needs-install' | 'server-disabled' | 'denied' | 'off' | 'on';

interface PushState {
  status: PushStatus;
  publicKey: string | null;
  preferences: PushPreferences | null;
  busy: boolean;
  error: string | null;
  /** 알림 설정 시트가 열려 있는지(헤더의 버튼으로 열고, 화면 영역 안에서 그린다) */
  settingsOpen: boolean;
  openSettings: () => void;
  closeSettings: () => void;
  /** 로그인 직후나 설정을 열 때: 이 기기의 상태를 확인하고 서버와 맞춘다 */
  refresh: () => Promise<void>;
  enable: () => Promise<void>;
  disable: () => Promise<void>;
  setPreference: (key: keyof PushPreferences, value: boolean) => Promise<void>;
  /** 시험 알림을 보내고 결과 문구를 돌려준다 */
  sendTest: () => Promise<string>;
  reset: () => void;
}

const INITIAL = { status: 'loading' as PushStatus, publicKey: null, preferences: null, busy: false, error: null, settingsOpen: false };

function messageOf(failure: unknown, fallback: string): string {
  return failure instanceof Error && failure.message !== '' ? failure.message : fallback;
}

/** 가장 마지막에 시작한 확인의 결과만 반영한다(로그아웃·재로그인과 겹쳐도 옛 결과가 덮어쓰지 않게) */
let latestRefresh = 0;

export const usePushStore = create<PushState>((set, get) => ({
  ...INITIAL,

  openSettings: () => set({ settingsOpen: true }),
  closeSettings: () => set({ settingsOpen: false }),

  refresh: async () => {
    const request = ++latestRefresh;
    const apply = (state: Partial<PushState>) => {
      if (request === latestRefresh) {
        set(state);
      }
    };
    const support = pushClient.detectPushSupport();
    if (support !== 'supported') {
      apply({ status: support, error: null });
      return;
    }
    try {
      const config = await pushApi.getPushConfig();
      if (!config.enabled) {
        apply({ status: 'server-disabled', publicKey: null, error: null });
        return;
      }
      if (pushClient.notificationPermission() === 'denied') {
        apply({ status: 'denied', publicKey: config.publicKey, error: null });
        return;
      }
      const on = await pushClient.syncThisDevice(config.publicKey);
      const preferences = on ? await pushApi.getPushPreferences() : null;
      apply({ status: on ? 'on' : 'off', publicKey: config.publicKey, preferences, error: null });
    } catch (failure) {
      console.error('Failed to refresh push state', failure);
      apply({ error: messageOf(failure, '알림 상태를 확인하지 못했어요.'), status: get().status === 'loading' ? 'off' : get().status });
    }
  },

  enable: async () => {
    const { publicKey } = get();
    if (publicKey === null) {
      return;
    }
    set({ busy: true, error: null });
    try {
      await pushClient.enableThisDevice(publicKey);
      const preferences = await pushApi.getPushPreferences();
      set({ status: 'on', preferences, busy: false });
    } catch (failure) {
      console.warn('Failed to enable push', failure);
      set({
        busy: false,
        status: pushClient.notificationPermission() === 'denied' ? 'denied' : get().status,
        error: messageOf(failure, '알림을 켜지 못했어요.'),
      });
    }
  },

  disable: async () => {
    set({ busy: true, error: null });
    try {
      await pushClient.disableThisDevice();
      set({ status: 'off', preferences: null, busy: false });
    } catch (failure) {
      console.warn('Failed to disable push', failure);
      set({ busy: false, error: messageOf(failure, '알림을 끄지 못했어요.') });
    }
  },

  setPreference: async (key, value) => {
    const current = get().preferences;
    if (current === null) {
      return;
    }
    const next = { ...current, [key]: value };
    set({ preferences: next, error: null });   // 바로 보여 주고, 저장에 실패하면 되돌린다
    try {
      set({ preferences: await pushApi.savePushPreferences(next) });
    } catch (failure) {
      console.warn('Failed to save push preferences', failure);
      set({ preferences: current, error: messageOf(failure, '설정을 저장하지 못했어요.') });
    }
  },

  sendTest: async () => {
    set({ busy: true, error: null });
    try {
      const result = await pushApi.sendTestPush();
      set({ busy: false });
      if (result.devices === 0) {
        return '알림을 받는 기기가 없어요.';
      }
      return result.delivered === result.devices
        ? `시험 알림을 보냈어요. (${result.delivered}대)`
        : `${result.devices}대 중 ${result.delivered}대에 보냈어요. 나머지는 잠시 후 다시 시도해 보세요.`;
    } catch (failure) {
      console.warn('Failed to send test push', failure);
      set({ busy: false });
      return messageOf(failure, '시험 알림을 보내지 못했어요.');
    }
  },

  reset: () => {
    latestRefresh += 1;
    set({ ...INITIAL });
  },
}));
