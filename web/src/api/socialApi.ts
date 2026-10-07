import { apiClient } from './client';
import type {
  FriendInvitePreview,
  FriendsOverview,
  GroupDetail,
  GroupInvitePreview,
  GroupSummary,
  InviteLink,
  JoinResult,
  MapGroupView,
  MapMember,
  PartyMap,
  Pin,
  RecommendationResult,
  RequestResult,
  ShareRole,
  ShareView,
  UserProfile,
} from './types';

/** 서버의 공통 응답 봉투 { success, data, timestamp } */
interface Envelope<T> {
  success: boolean;
  data: T;
}

// ---------------------------------------------------------------- 내 프로필

export async function updateProfile(nickname: string, username: string): Promise<UserProfile> {
  const response = await apiClient.patch<Envelope<UserProfile>>('/me', { nickname, username });
  return response.data.data;
}

// ---------------------------------------------------------------- 친구

export async function getFriends(signal?: AbortSignal): Promise<FriendsOverview> {
  const response = await apiClient.get<Envelope<FriendsOverview>>('/friends', { signal });
  return response.data.data;
}

export async function requestFriend(username: string): Promise<RequestResult> {
  const response = await apiClient.post<Envelope<RequestResult>>('/friends/requests', { username });
  return response.data.data;
}

export async function acceptFriendRequest(requestId: string): Promise<void> {
  await apiClient.post(`/friends/requests/${requestId}/accept`);
}

/** 받은 요청 거절 / 보낸 요청 취소 */
export async function removeFriendRequest(requestId: string): Promise<void> {
  await apiClient.delete(`/friends/requests/${requestId}`);
}

export async function unfriend(userId: string): Promise<void> {
  await apiClient.delete(`/friends/${userId}`);
}

export async function getFriendInvite(signal?: AbortSignal): Promise<InviteLink | null> {
  const response = await apiClient.get<Envelope<InviteLink | null>>('/friends/invite', { signal });
  return response.data.data;
}

export async function createFriendInvite(): Promise<InviteLink> {
  const response = await apiClient.post<Envelope<InviteLink>>('/friends/invite');
  return response.data.data;
}

export async function revokeFriendInvite(): Promise<void> {
  await apiClient.delete('/friends/invite');
}

export async function previewFriendInvite(code: string, signal?: AbortSignal): Promise<FriendInvitePreview> {
  const response = await apiClient.get<Envelope<FriendInvitePreview>>(`/friends/invite/${encodeURIComponent(code)}`, { signal });
  return response.data.data;
}

export async function acceptFriendInvite(code: string): Promise<void> {
  await apiClient.post(`/friends/invite/${encodeURIComponent(code)}/accept`);
}

// ---------------------------------------------------------------- 지도 공유(친구)

export async function listShares(mapId: string, signal?: AbortSignal): Promise<ShareView[]> {
  const response = await apiClient.get<Envelope<ShareView[]>>(`/maps/${mapId}/shares`, { signal });
  return response.data.data;
}

export async function shareMap(mapId: string, userId: string, role: ShareRole): Promise<ShareView> {
  const response = await apiClient.put<Envelope<ShareView>>(`/maps/${mapId}/shares/${userId}`, { role });
  return response.data.data;
}

/** 주인이 공유를 끊거나, 공유받은 본인이 나간다 */
export async function revokeShare(mapId: string, userId: string): Promise<void> {
  await apiClient.delete(`/maps/${mapId}/shares/${userId}`);
}

export async function listMapMembers(mapId: string, signal?: AbortSignal): Promise<MapMember[]> {
  const response = await apiClient.get<Envelope<MapMember[]>>(`/maps/${mapId}/members`, { signal });
  return response.data.data;
}

// ---------------------------------------------------------------- 지도를 모임에 공유

export async function listGroupsOfMap(mapId: string, signal?: AbortSignal): Promise<MapGroupView[]> {
  const response = await apiClient.get<Envelope<MapGroupView[]>>(`/maps/${mapId}/groups`, { signal });
  return response.data.data;
}

export async function shareMapWithGroup(mapId: string, groupId: string): Promise<MapGroupView> {
  const response = await apiClient.put<Envelope<MapGroupView>>(`/maps/${mapId}/groups/${groupId}`);
  return response.data.data;
}

export async function unshareMapFromGroup(mapId: string, groupId: string): Promise<void> {
  await apiClient.delete(`/maps/${mapId}/groups/${groupId}`);
}

// ---------------------------------------------------------------- 모임

export async function listGroups(signal?: AbortSignal): Promise<GroupSummary[]> {
  const response = await apiClient.get<Envelope<GroupSummary[]>>('/groups', { signal });
  return response.data.data;
}

export async function createGroup(name: string): Promise<GroupSummary> {
  const response = await apiClient.post<Envelope<GroupSummary>>('/groups', { name });
  return response.data.data;
}

export async function getGroup(groupId: string, signal?: AbortSignal): Promise<GroupDetail> {
  const response = await apiClient.get<Envelope<GroupDetail>>(`/groups/${groupId}`, { signal });
  return response.data.data;
}

export async function renameGroup(groupId: string, name: string): Promise<void> {
  await apiClient.patch(`/groups/${groupId}`, { name });
}

export async function deleteGroup(groupId: string): Promise<void> {
  await apiClient.delete(`/groups/${groupId}`);
}

export async function leaveGroup(groupId: string): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/members/me`);
}

export async function kickMember(groupId: string, userId: string): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/members/${userId}`);
}

export async function inviteFriendToGroup(groupId: string, userId: string): Promise<void> {
  await apiClient.post(`/groups/${groupId}/members`, { userId });
}

export async function transferGroupOwner(groupId: string, userId: string): Promise<void> {
  await apiClient.put(`/groups/${groupId}/owner`, { userId });
}

export async function listGroupMaps(groupId: string, signal?: AbortSignal): Promise<PartyMap[]> {
  const response = await apiClient.get<Envelope<PartyMap[]>>(`/groups/${groupId}/maps`, { signal });
  return response.data.data;
}

export async function getGroupInvite(groupId: string, signal?: AbortSignal): Promise<InviteLink | null> {
  const response = await apiClient.get<Envelope<InviteLink | null>>(`/groups/${groupId}/invite`, { signal });
  return response.data.data;
}

export async function createGroupInvite(groupId: string): Promise<InviteLink> {
  const response = await apiClient.post<Envelope<InviteLink>>(`/groups/${groupId}/invite`);
  return response.data.data;
}

export async function revokeGroupInvite(groupId: string): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/invite`);
}

export async function previewGroupInvite(code: string, signal?: AbortSignal): Promise<GroupInvitePreview> {
  const response = await apiClient.get<Envelope<GroupInvitePreview>>(`/groups/invite/${encodeURIComponent(code)}`, { signal });
  return response.data.data;
}

export async function joinGroup(code: string): Promise<JoinResult> {
  const response = await apiClient.post<Envelope<JoinResult>>(`/groups/invite/${encodeURIComponent(code)}/join`);
  return response.data.data;
}

// ---------------------------------------------------------------- 겹쳐보기

export interface OverlayResult {
  /** 실제로 겹친 지도(요청한 것 중 내가 볼 수 있는 것) */
  mapIds: string[];
  pins: Pin[];
}

/** 고른 지도들의 핀을 한 번에 가져온다. 볼 수 없는 지도는 서버가 조용히 뺀다. */
export async function getOverlay(mapIds: string[], signal?: AbortSignal): Promise<OverlayResult> {
  const response = await apiClient.get<Envelope<OverlayResult>>('/overlay/pins', { params: { mapIds: mapIds.join(',') }, signal });
  return response.data.data;
}

export interface RecommendationOptions {
  /** 이 사람들의 핀은 빼고 계산한다 */
  excludeAuthors: string[];
  /** 이 인원 이상이 찍은 장소만 */
  minPeople: number;
}

/** 고른 지도들(내가 볼 수 있는 것만)의 추천 장소. 점수 내역이 함께 온다. */
export async function getRecommendations(mapIds: string[], options: RecommendationOptions, signal?: AbortSignal): Promise<RecommendationResult> {
  const params: Record<string, string | number> = { mapIds: mapIds.join(','), minPeople: options.minPeople };
  if (options.excludeAuthors.length > 0) {
    params.excludeAuthors = options.excludeAuthors.join(',');
  }
  const response = await apiClient.get<Envelope<RecommendationResult>>('/overlay/recommendations', { params, signal });
  return response.data.data;
}
