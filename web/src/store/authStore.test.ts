import { afterEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import { API_BASE } from '../config';
import { buildLoginUrl, useAuthStore } from './authStore';

afterEach(() => {
  vi.restoreAllMocks();
  useAuthStore.setState({ user: null, isAuthenticated: false, initialized: false });
});

describe('buildLoginUrl', () => {
  it('돌아올 경로를 인코딩해 API 기준 경로 아래 로그인 시작 주소를 만든다', () => {
    expect(buildLoginUrl('/maps/42?tab=a b')).toBe(`${API_BASE}/bff/login?return=%2Fmaps%2F42%3Ftab%3Da%20b`);
  });
});

describe('loadSession', () => {
  it('서버가 로그인 상태라고 하면 사용자를 반영한다', async () => {
    const user = { id: 'u1', username: 'alice', nickname: 'alice', color: '#E4572E' };
    vi.spyOn(axios, 'get').mockResolvedValue({ data: { data: { authenticated: true, user } } });

    await useAuthStore.getState().loadSession();

    expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, user, initialized: true });
  });

  it('서버에 닿지 못해도 비로그인으로 시작하고 초기화는 끝난다', async () => {
    vi.spyOn(axios, 'get').mockRejectedValue(new Error('network'));
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    await useAuthStore.getState().loadSession();

    expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: false, user: null, initialized: true });
  });
});

describe('signOut', () => {
  it('CSRF 헤더와 함께 로그아웃을 요청하고 상태를 비운다', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({ data: {} });
    useAuthStore.setState({ user: { id: 'u1', username: 'a', nickname: 'a', color: '#fff' }, isAuthenticated: true });

    await useAuthStore.getState().signOut();

    expect(post).toHaveBeenCalledWith(`${API_BASE}/bff/logout`, null, { headers: { 'X-Party-Csrf': '1' } });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});

describe('hasDefaultNickname', () => {
  it('닉네임이 임시 사용자명과 같으면 정하도록 안내한다', async () => {
    const { hasDefaultNickname } = await import('./authStore');
    expect(hasDefaultNickname({ id: 'u', username: 'userabcd1234', nickname: 'userabcd1234', color: '#fff' })).toBe(true);
    expect(hasDefaultNickname({ id: 'u', username: 'userabcd1234', nickname: '파티왕', color: '#fff' })).toBe(false);
    expect(hasDefaultNickname(null)).toBe(false);
  });
});
