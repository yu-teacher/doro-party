import { describe, expect, it } from 'vitest';
import { canManageMap, canPlacePins, mapOrigin } from './mapRole';

describe('지도 권한', () => {
  it('주인과 편집자만 핀을 꽂고, 지도 관리는 주인만 한다', () => {
    expect(canPlacePins('OWNER')).toBe(true);
    expect(canPlacePins('EDITOR')).toBe(true);
    expect(canPlacePins('VIEWER')).toBe(false);
    expect(canPlacePins(undefined)).toBe(false);
    expect(canManageMap('OWNER')).toBe(true);
    expect(canManageMap('EDITOR')).toBe(false);
    expect(canManageMap('VIEWER')).toBe(false);
  });
});

describe('mapOrigin', () => {
  it('내가 만든 지도 / 친구가 직접 공유한 지도 / 모임으로 보이는 지도를 구분한다', () => {
    expect(mapOrigin({ role: 'OWNER', viaGroups: [] })).toBe('mine');
    expect(mapOrigin({ role: 'EDITOR', viaGroups: [] })).toBe('friend');
    expect(mapOrigin({ role: 'VIEWER', viaGroups: [] })).toBe('friend');
    expect(mapOrigin({ role: 'VIEWER', viaGroups: ['홍대 모임'] })).toBe('group');
  });
});
