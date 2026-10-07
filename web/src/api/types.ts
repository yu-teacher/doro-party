export interface UserProfile {
  id: string;
  username: string;
  nickname: string;
  /** 겹쳐보기에서 이 사람의 핀을 구분하는 색(#RRGGBB) */
  color: string;
}

/** 다른 사람에게 보여 주는 최소한의 정보(이메일 같은 계정 정보는 없다) */
export interface UserSummary {
  id: string;
  username: string;
  nickname: string;
  color: string;
}

/** 이 지도에서 내 권한: 주인 / 핀을 꽂을 수 있는 편집자 / 보기만 하는 열람자 */
export type MapRole = 'OWNER' | 'EDITOR' | 'VIEWER';

/** 친구에게 지도를 공유할 때의 권한 */
export type ShareRole = 'VIEWER' | 'EDITOR';

/** 핀의 상태: 가고 싶은 곳 / 다녀온 곳 */
export type PinStatus = 'WISH' | 'VISITED';

/** 다녀온 뒤의 재방문 의사: 또 가고 싶어요 / 한 번이면 충분해요 */
export type RevisitIntent = 'AGAIN' | 'ONCE';

export interface PartyMap {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  ownerNickname: string;
  ownerColor: string;
  /** 내가 만든 지도인지(role 이 OWNER 인지와 같다) */
  mine: boolean;
  role: MapRole;
  /** 이 지도를 내가 볼 수 있게 해 준 모임들의 이름(직접 만들었거나 직접 공유받았으면 비어 있다) */
  viaGroups: string[];
  pinCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Pin {
  id: string;
  mapId: string;
  createdBy: string;
  authorNickname: string;
  authorColor: string;
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
  nickname: 20,
  groupName: 30,
  username: { min: 3, max: 30 },
  visitNote: 500,
  privateNote: 2000,
  /** 사진을 올리기 전에 줄이는 한 변의 최대 길이(px) */
  photoMaxEdge: 1600,
  photosPerPin: 10,
} as const;


// ---------------------------------------------------------------- 친구

export interface FriendView {
  user: UserSummary;
  since: string;
}

/** 받은 요청이면 user 는 보낸 사람, 보낸 요청이면 받는 사람이다 */
export interface FriendRequestView {
  id: string;
  user: UserSummary;
  requestedAt: string;
}

export interface FriendsOverview {
  friends: FriendView[];
  incoming: FriendRequestView[];
  outgoing: FriendRequestView[];
}

export interface RequestResult {
  status: 'PENDING' | 'ACCEPTED';
  user: UserSummary;
}

export interface InviteLink {
  code: string;
  expiresAt: string;
}

export interface FriendInvitePreview {
  inviter: UserSummary;
  self: boolean;
  alreadyFriends: boolean;
}

// ---------------------------------------------------------------- 지도 공유

export interface ShareView {
  user: UserSummary;
  role: ShareRole;
  sharedAt: string;
}

/** 이 지도를 같이 보는 사람(주인 + 공유받은 사람). 사용자명은 보이지 않는다 */
export interface MapMember {
  userId: string;
  nickname: string;
  color: string;
  role: MapRole;
}

export interface MapGroupView {
  groupId: string;
  groupName: string;
  sharedAt: string;
}

// ---------------------------------------------------------------- 모임

export type GroupRole = 'OWNER' | 'MEMBER';

export interface GroupSummary {
  id: string;
  name: string;
  myRole: GroupRole;
  memberCount: number;
  mapCount: number;
  ownerNickname: string;
  createdAt: string;
}

export interface GroupMember {
  userId: string;
  nickname: string;
  color: string;
  role: GroupRole;
  joinedAt: string;
}

export interface GroupDetail {
  id: string;
  name: string;
  myRole: GroupRole;
  members: GroupMember[];
  mapCount: number;
  createdAt: string;
}

export interface GroupInvitePreview {
  groupName: string;
  memberCount: number;
  ownerNickname: string;
  alreadyMember: boolean;
  full: boolean;
}

export interface JoinResult {
  groupId: string;
  groupName: string;
}

// ---------------------------------------------------------------- 모임 추천

/** 점수가 어떻게 나왔는지의 내역: 항목마다 "몇 명 x 가중치 = 점수" 이고 합이 장소의 점수다 */
export interface ScoreBreakdown {
  people: number;
  peoplePoints: number;
  wishCount: number;
  wishPoints: number;
  visitedCount: number;
  visitedPoints: number;
  againCount: number;
  againPoints: number;
  onceCount: number;
  oncePoints: number;
  ratedCount: number;
  ratingAverage: number | null;
  ratingPoints: number;
}

/** 이 장소에서 한 사람의 입장 */
export interface PlaceAuthor {
  userId: string;
  nickname: string;
  color: string;
  status: PinStatus;
  rating: number | null;
  revisitIntent: RevisitIntent | null;
}

export interface RecommendedPlace {
  rank: number;
  lat: number;
  lng: number;
  name: string;
  otherNames: string[];
  people: number;
  pinCount: number;
  score: number;
  breakdown: ScoreBreakdown;
  authors: PlaceAuthor[];
  pinIds: string[];
  mapIds: string[];
}

export interface RecommendationResult {
  /** 실제로 추천에 쓴 지도(요청한 것 중 내가 볼 수 있는 것) */
  mapIds: string[];
  places: RecommendedPlace[];
}
