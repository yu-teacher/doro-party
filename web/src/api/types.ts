export interface UserProfile {
  id: string;
  username: string;
  nickname: string;
  /** 겹쳐보기에서 이 사람의 핀을 구분하는 색(#RRGGBB) */
  color: string;
}

/** 핀의 상태: 가고 싶은 곳 / 다녀온 곳 */
export type PinStatus = 'WISH' | 'VISITED';

export interface PartyMap {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  /** 내가 만든 지도인지 */
  mine: boolean;
  pinCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Pin {
  id: string;
  mapId: string;
  createdBy: string;
  lat: number;
  lng: number;
  name: string;
  sharedMemo: string | null;
  status: PinStatus;
  rating: number | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface MapInput {
  name: string;
  description: string | null;
}

export interface PinInput {
  name: string;
  sharedMemo: string | null;
  lat: number;
  lng: number;
  status: PinStatus;
  rating: number | null;
  tags: string[];
}

/** 서버 입력 제한(백엔드 PinDtos/MapDtos 와 같은 값). 어긋나면 서버가 400 으로 거부한다. */
export const LIMITS = {
  mapName: 100,
  mapDescription: 500,
  pinName: 100,
  pinMemo: 2000,
  tag: 30,
  tagsPerPin: 10,
  ratingMin: 1,
  ratingMax: 5,
} as const;
