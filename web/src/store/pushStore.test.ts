import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as pushApi from '../api/pushApi';
import * as pushClient from '../push/pushClient';
import { usePushStore } from './pushStore';

vi.mock('../api/pushApi');
vi.mock('../push/pushClient');
const api = vi.mocked(pushApi);
const client = vi.mocked(pushClient);

const PREFS = { comments: true, friendRequests: true, shares: false };

beforeEach(() => {
  usePushStore.getState().reset();
  vi.resetAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  client.detectPushSupport.mockReturnValue('supported');
  client.notificationPermission.mockReturnValue('default');
  api.getPushConfig.mockResolvedValue({ enabled: true, publicKey: 'KEY' });
  api.getPushPreferences.mockResolvedValue(PREFS);
  client.syncThisDevice.mockResolvedValue(false);
});

describe('상태 확인', () => {
  it.each(['unsupported', 'needs-install'] as const)('이 브라우저가 %s 이면 서버에 묻지 않고 그 상태가 된다', async (support) => {
    client.detectPushSupport.mockReturnValue(support);
    await usePushStore.getState().refresh();
    expect(usePushStore.getState().status).toBe(support);
    expect(api.getPushConfig).not.toHaveBeenCalled();
  });

  it('서버에 푸시 키가 없으면 켤 수 없다', async () => {
    api.getPushConfig.mockResolvedValue({ enabled: false, publicKey: '' });
    await usePushStore.getState().refresh();
    expect(usePushStore.getState().status).toBe('server-disabled');
  });

  it('권한을 막아 둔 기기는 denied', async () => {
    client.notificationPermission.mockReturnValue('denied');
    await usePushStore.getState().refresh();
    expect(usePushStore.getState().status).toBe('denied');
    expect(client.syncThisDevice).not.toHaveBeenCalled();
  });

  it('구독이 없으면 off, 있으면 서버와 맞추고 on 이 되어 알림 종류 설정을 불러온다', async () => {
    await usePushStore.getState().refresh();
    expect(usePushStore.getState()).toMatchObject({ status: 'off', preferences: null, publicKey: 'KEY' });

    client.syncThisDevice.mockResolvedValue(true);
    await usePushStore.getState().refresh();
    expect(usePushStore.getState()).toMatchObject({ status: 'on', preferences: PREFS });
    expect(client.syncThisDevice).toHaveBeenCalledWith('KEY');
  });

  it('확인에 실패하면 오류를 남기고 켜짐/꺼짐을 단정하지 않는다(처음이면 off 로 둔다)', async () => {
    api.getPushConfig.mockRejectedValue(new Error('네트워크 오류'));
    await usePushStore.getState().refresh();
    expect(usePushStore.getState()).toMatchObject({ error: '네트워크 오류', status: 'off' });
  });

  it('겹쳐서 시작한 확인은 가장 마지막 것만 반영한다(늦게 온 옛 결과가 덮어쓰지 않는다)', async () => {
    let finishFirst: (value: { enabled: boolean; publicKey: string }) => void = () => undefined;
    api.getPushConfig.mockImplementationOnce(() => new Promise((resolve) => { finishFirst = resolve; }));
    api.getPushConfig.mockResolvedValueOnce({ enabled: false, publicKey: '' });

    const first = usePushStore.getState().refresh();
    await usePushStore.getState().refresh();
    finishFirst({ enabled: true, publicKey: 'OLD' });
    await first;

    expect(usePushStore.getState().status).toBe('server-disabled');
  });

  it('비우면(로그아웃) 진행 중이던 확인 결과도 반영하지 않는다', async () => {
    let finish: (value: { enabled: boolean; publicKey: string }) => void = () => undefined;
    api.getPushConfig.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    const pending = usePushStore.getState().refresh();
    usePushStore.getState().reset();
    finish({ enabled: true, publicKey: 'KEY' });
    await pending;
    expect(usePushStore.getState().status).toBe('loading');
  });
});

describe('켜기·끄기', () => {
  beforeEach(async () => {
    await usePushStore.getState().refresh();   // off + publicKey
  });

  it('켜면 on 이 되고 알림 종류 설정을 불러온다', async () => {
    client.enableThisDevice.mockResolvedValue(undefined);
    await usePushStore.getState().enable();
    expect(client.enableThisDevice).toHaveBeenCalledWith('KEY');
    expect(usePushStore.getState()).toMatchObject({ status: 'on', preferences: PREFS, busy: false, error: null });
  });

  it('권한을 거부하면 denied 로 바뀌고 안내 문구가 남는다', async () => {
    client.enableThisDevice.mockRejectedValue(new Error('알림 권한이 허용되지 않았어요.'));
    client.notificationPermission.mockReturnValue('denied');
    await usePushStore.getState().enable();
    expect(usePushStore.getState()).toMatchObject({ status: 'denied', error: '알림 권한이 허용되지 않았어요.', busy: false });
  });

  it('켜기에 실패해도(권한 문제가 아님) 상태는 그대로이고 오류를 보여 준다', async () => {
    client.enableThisDevice.mockRejectedValue(new Error('서버 오류'));
    await usePushStore.getState().enable();
    expect(usePushStore.getState()).toMatchObject({ status: 'off', error: '서버 오류' });
  });

  it('끄면 off 가 되고 설정은 비운다. 실패하면 켜진 채로 오류를 보여 준다', async () => {
    client.enableThisDevice.mockResolvedValue(undefined);
    await usePushStore.getState().enable();

    client.disableThisDevice.mockRejectedValue(new Error('삭제 실패'));
    await usePushStore.getState().disable();
    expect(usePushStore.getState()).toMatchObject({ status: 'on', error: '삭제 실패' });

    client.disableThisDevice.mockResolvedValue(undefined);
    await usePushStore.getState().disable();
    expect(usePushStore.getState()).toMatchObject({ status: 'off', preferences: null });
  });
});

describe('알림 종류와 시험 알림', () => {
  beforeEach(async () => {
    client.syncThisDevice.mockResolvedValue(true);
    await usePushStore.getState().refresh();
  });

  it('종류를 바꾸면 바로 보여 주고 서버가 돌려준 값으로 확정한다', async () => {
    api.savePushPreferences.mockResolvedValue({ ...PREFS, comments: false });
    const saving = usePushStore.getState().setPreference('comments', false);
    expect(usePushStore.getState().preferences?.comments).toBe(false);   // 저장 전에 이미 반영
    await saving;
    expect(api.savePushPreferences).toHaveBeenCalledWith({ ...PREFS, comments: false });
    expect(usePushStore.getState().preferences).toEqual({ ...PREFS, comments: false });
  });

  it('저장에 실패하면 이전 값으로 되돌리고 오류를 보여 준다', async () => {
    api.savePushPreferences.mockRejectedValue(new Error('저장 실패'));
    await usePushStore.getState().setPreference('shares', true);
    expect(usePushStore.getState().preferences).toEqual(PREFS);
    expect(usePushStore.getState().error).toBe('저장 실패');
  });

  it('시험 알림 결과를 문구로 돌려준다: 기기 없음, 모두 전달, 일부만 전달, 실패', async () => {
    api.sendTestPush.mockResolvedValueOnce({ devices: 0, delivered: 0 });
    expect(await usePushStore.getState().sendTest()).toBe('알림을 받는 기기가 없어요.');
    api.sendTestPush.mockResolvedValueOnce({ devices: 2, delivered: 2 });
    expect(await usePushStore.getState().sendTest()).toContain('2대');
    api.sendTestPush.mockResolvedValueOnce({ devices: 3, delivered: 1 });
    expect(await usePushStore.getState().sendTest()).toContain('3대 중 1대');
    api.sendTestPush.mockRejectedValueOnce(new Error('서버 오류'));
    expect(await usePushStore.getState().sendTest()).toBe('서버 오류');
    expect(usePushStore.getState().busy).toBe(false);
  });
});
