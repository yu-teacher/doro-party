import { create } from 'zustand';
import axios from 'axios';
import { API_BASE } from '../config';
import { CSRF_HEADER, CSRF_VALUE } from '../api/csrf';
import type { UserProfile } from '../api/types';

/**
 * 로그인 상태. 로그인과 토큰 관리는 서버(BFF)가 맡는다: 브라우저는 HttpOnly 세션 쿠키만 가지고 있어서
 * 여기에는 토큰이 없고, 서버가 알려 주는 "누구로 로그인했는가" 만 있다.
 */
interface AuthState {
  user: UserProfile | null;
  isAuthenticated: boolean;
  /** 앱을 처음 그리기 전에 서버에 로그인 상태를 확인했는지 */
  initialized: boolean;
  /** 서버에 현재 로그인 상태를 물어 반영한다. 앱 시작 때 한 번 호출한다. */
  loadSession: () => Promise<void>;
  signOut: () => Promise<void>;
}

interface SessionResponse {
  data?: { authenticated?: boolean; user?: UserProfile | null };
}

const BFF_BASE = `${API_BASE}/bff`;
const SIGNED_OUT = { user: null, isAuthenticated: false } as const;

/** 로그인 후 돌아올 곳을 담은 로그인 시작 주소. 서버가 사이트 안의 경로인지 한 번 더 검증한다. */
export function buildLoginUrl(returnPath: string): string {
  return `${BFF_BASE}/login?return=${encodeURIComponent(returnPath)}`;
}

export const useAuthStore = create<AuthState>((set) => ({
  ...SIGNED_OUT,
  initialized: false,

  loadSession: async () => {
    try {
      const response = await axios.get<SessionResponse>(`${BFF_BASE}/session`, { headers: { [CSRF_HEADER]: CSRF_VALUE } });
      const data = response.data?.data;
      set(data?.authenticated && data.user ? { user: data.user, isAuthenticated: true } : SIGNED_OUT);
    } catch (error) {
      // 서버에 닿지 못해도 앱은 비로그인 상태로 뜬다
      console.warn('Failed to load the login session', error);
      set(SIGNED_OUT);
    } finally {
      set({ initialized: true });
    }
  },

  signOut: async () => {
    try {
      await axios.post(`${BFF_BASE}/logout`, null, { headers: { [CSRF_HEADER]: CSRF_VALUE } });
    } catch (error) {
      // 서버 세션을 못 지웠어도 화면은 로그아웃으로 보여 주되, 원인은 남긴다
      console.warn('Logout request failed', error);
    }
    set(SIGNED_OUT);
  },
}));
