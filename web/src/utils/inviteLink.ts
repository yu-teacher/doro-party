import { ROUTER_BASENAME } from '../config';

/** 초대 링크의 종류: 친구 초대는 /invite/{code}, 모임 초대는 /join/{code}. 앱 경로이고 서버 로그인 복귀 경로로도 쓴다. */
export type InviteKind = 'friend' | 'group';

const SEGMENT: Record<InviteKind, string> = { friend: 'invite', group: 'join' };

/** 앱 안의 경로(라우터 기준). 로그인 후 돌아올 곳으로도 쓴다. */
export function invitePath(kind: InviteKind, code: string): string {
  return `/${SEGMENT[kind]}/${encodeURIComponent(code)}`;
}

/** 친구에게 보낼 전체 주소(https://도메인/party/invite/코드). */
export function inviteUrl(origin: string, kind: InviteKind, code: string): string {
  return `${origin}${ROUTER_BASENAME}${invitePath(kind, code)}`;
}
