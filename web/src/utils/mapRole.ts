import type { MapRole, PartyMap } from '../api/types';

/** 핀을 꽂을 수 있는 권한(주인·편집자). 열람자는 보기만 한다. */
export function canPlacePins(role: MapRole | undefined): boolean {
  return role === 'OWNER' || role === 'EDITOR';
}

/** 지도 이름·설명을 고치고 공유를 관리할 수 있는 권한(주인만). */
export function canManageMap(role: MapRole | undefined): boolean {
  return role === 'OWNER';
}

export type MapOrigin = 'mine' | 'friend' | 'group';

/** 지도 선택 목록에서 묶는 기준: 내 지도 / 친구가 직접 공유한 지도 / 모임 덕분에 보이는 지도. */
export function mapOrigin(map: Pick<PartyMap, 'role' | 'viaGroups'>): MapOrigin {
  if (map.role === 'OWNER') {
    return 'mine';
  }
  return map.viaGroups.length > 0 ? 'group' : 'friend';
}

export const ORIGIN_LABEL: Record<MapOrigin, string> = {
  mine: '내 지도',
  friend: '친구가 공유한 지도',
  group: '모임에 공유된 지도',
};

export const ROLE_LABEL: Record<MapRole, string> = {
  OWNER: '내 지도',
  EDITOR: '편집 가능',
  VIEWER: '보기 전용',
};
