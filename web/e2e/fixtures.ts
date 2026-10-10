import type { Page, Route } from '@playwright/test';

const API = '/party/api/v1';
const LONG = 'x'.repeat(48);
const NOW = '2026-10-01T09:00:00Z';

const ME = { id: 'u-me', username: 'doro', nickname: '도로롱', color: '#10b981' };
const user = (id: string, nickname: string, color = '#3b82f6') => ({ id, username: id, nickname, color });

const map = (id: string, overrides: Record<string, unknown> = {}) => ({
  id, name: `지도 ${id}`, description: null, ownerId: ME.id, ownerNickname: ME.nickname, ownerColor: ME.color, mine: true, role: 'OWNER',
  viaGroups: [], friendAccess: 'NONE', pinCount: 5, createdAt: NOW, updatedAt: NOW, ...overrides,
});
const maps = [
  map('m1', { name: `이름이 아주 긴 지도 ${LONG}${LONG}`, description: `설명도 긴 문자열 ${LONG}${LONG}`, viaGroups: [`모임 ${LONG}`] }),
  map('m2', { name: '맛집 지도', ownerId: 'u-f1', ownerNickname: `친구${LONG}`, mine: false, role: 'VIEWER', friendAccess: 'VIEWER' }),
];

const groups = [
  { id: 'g1', name: `모임 이름이 아주 긴 경우 ${LONG}`, myRole: 'OWNER', memberCount: 4, mapCount: 2, ownerNickname: ME.nickname, createdAt: NOW },
  { id: 'g2', name: '주말 모임', myRole: 'MEMBER', memberCount: 8, mapCount: 1, ownerNickname: `모임장${LONG}`, createdAt: NOW },
];
const groupDetail = {
  id: 'g1', name: groups[0].name, myRole: 'OWNER', mapCount: 2, createdAt: NOW,
  members: [
    { userId: ME.id, nickname: ME.nickname, color: ME.color, role: 'OWNER', joinedAt: NOW },
    { userId: 'u-f1', nickname: `멤버닉네임${LONG}`, color: '#f59e0b', role: 'MEMBER', joinedAt: NOW },
    { userId: 'u-f2', nickname: '민수', color: '#3b82f6', role: 'MEMBER', joinedAt: NOW },
  ],
};

const friends = {
  friends: [{ user: user('u-f1', `친구닉네임${LONG}`), since: NOW }, { user: user('u-f2', '민수', '#f59e0b'), since: NOW }],
  incoming: [{ id: 'r1', user: user('u-f3', `요청보낸사람${LONG}`), requestedAt: NOW }],
  outgoing: [{ id: 'r2', user: user('u-f4', '영희'), requestedAt: NOW }],
};

const envelope = (data: unknown) => ({ success: true, data, timestamp: NOW });
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

export interface MockOptions {
  loggedIn: boolean;
}

/** 파티 서버(BFF 세션 포함) 응답을 가짜로 대체한다. 모르는 요청은 404 로 두어 예상치 못한 호출을 드러낸다. */
export async function mockApi(page: Page, { loggedIn }: MockOptions): Promise<void> {
  await page.route((url) => url.pathname.startsWith(API), async (route) => {
    const request = route.request();
    if (!['xhr', 'fetch'].includes(request.resourceType())) return route.fallback();
    const path = decodeURIComponent(new URL(request.url()).pathname.slice(API.length));
    const method = request.method();

    if (path === '/bff/session') return json(route, envelope(loggedIn ? { authenticated: true, user: ME } : { authenticated: false, user: null }));
    if (!loggedIn) return json(route, { success: false, code: 'UNAUTHORIZED' }, 401);
    if (path === '/maps' && method === 'GET') return json(route, envelope(maps));
    if (/^\/maps\/[^/]+$/.test(path)) return json(route, envelope(maps[0]));
    if (/^\/maps\/[^/]+\/pins$/.test(path)) return json(route, envelope([]));
    if (/^\/maps\/[^/]+\/(members|groups|shares|private-notes)$/.test(path)) return json(route, envelope([]));
    if (path === '/comments/unread') return json(route, envelope([]));
    if (path === '/overlay/pins') return json(route, envelope({ pins: [], maps: [] }));
    if (path === '/friends') return json(route, envelope(friends));
    if (path === '/friends/invite') return json(route, envelope({ code: 'ABCD1234', expiresAt: '2026-12-01T00:00:00Z' }));
    if (path === '/friends/maps') return json(route, envelope({ friends: [{ friend: user('u-f1', `친구닉네임${LONG}`), maps: [maps[1]] }, { friend: user('u-f2', '민수'), maps: [] }] }));
    if (/^\/friends\/invite\/[^/]+$/.test(path)) return json(route, envelope({ inviter: user('u-f9', `초대한사람${LONG}`), self: false, alreadyFriends: false }));
    if (path === '/groups' && method === 'GET') return json(route, envelope(groups));
    if (path === '/groups/g1') return json(route, envelope(groupDetail));
    if (path === '/groups/g1/maps') return json(route, envelope(maps));
    if (path === '/groups/g1/invite') return json(route, envelope({ code: 'GROUP1234', expiresAt: '2026-12-01T00:00:00Z' }));
    if (/^\/groups\/invite\/[^/]+$/.test(path)) return json(route, envelope({ groupName: groups[0].name, memberCount: 4, ownerNickname: `모임장${LONG}`, alreadyMember: false, full: false }));
    return json(route, { success: false, code: 'NOT_MOCKED', message: `${method} ${path}` }, 404);
  });
}
