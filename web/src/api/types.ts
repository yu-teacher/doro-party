export interface UserProfile {
  id: string;
  username: string;
  nickname: string;
  /** 겹쳐보기에서 이 사람의 핀을 구분하는 색(#RRGGBB) */
  color: string;
}

/** 핀의 상태: 가고 싶은 곳 / 다녀온 곳 */
export type PinStatus = 'WISH' | 'VISITED';

/** 다녀온 뒤의 재방문 의사: 또 가고 싶어요 / 한 번이면 충분해요 */
export type RevisitIntent = 'AGAIN' | 'ONCE';

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
  revisitIntent: RevisitIntent | null;
  tags: string[];
  /** 방문 기록 수, 마지막 방문일(yyyy-MM-dd), 사진 수 */
  visitCount: number;
  lastVisitedOn: string | null;
  photoCount: number;
  createdAt: string;
  updatedAt: string;
}

/** 핀을 다녀온 기록 한 번 */
export interface Visit {
  id: string;
  pinId: string;
  userId: string;
  /** yyyy-MM-dd */
  visitedOn: string;
  note: string | null;
  createdAt: string;
}

/** 쓴 사람에게만 보이는 핀 메모 */
export interface PrivateNote {
  pinId: string;
  body: string;
  updatedAt: string;
}

export interface Photo {
  id: string;
  pinId: string;
  uploadedBy: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
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
  revisitIntent: RevisitIntent | null;
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
  visitNote: 500,
  privateNote: 2000,
  /** 사진을 올리기 전에 줄이는 한 변의 최대 길이(px) */
  photoMaxEdge: 1600,
  photosPerPin: 10,
} as const;
