import { apiClient } from './client';
import type { PinComment, UnreadPin } from './types';

/** 서버의 공통 응답 봉투 { success, data, timestamp } */
interface Envelope<T> {
  success: boolean;
  data: T;
}

const commentsPath = (mapId: string, pinId: string) => `/maps/${mapId}/pins/${pinId}/comments`;

export async function listComments(mapId: string, pinId: string, signal?: AbortSignal): Promise<PinComment[]> {
  const response = await apiClient.get<Envelope<PinComment[]>>(commentsPath(mapId, pinId), { signal });
  return response.data.data;
}

export async function addComment(mapId: string, pinId: string, body: string): Promise<PinComment> {
  const response = await apiClient.post<Envelope<PinComment>>(commentsPath(mapId, pinId), { body });
  return response.data.data;
}

export async function editComment(mapId: string, pinId: string, commentId: string, body: string): Promise<PinComment> {
  const response = await apiClient.patch<Envelope<PinComment>>(`${commentsPath(mapId, pinId)}/${commentId}`, { body });
  return response.data.data;
}

export async function deleteComment(mapId: string, pinId: string, commentId: string): Promise<void> {
  await apiClient.delete(`${commentsPath(mapId, pinId)}/${commentId}`);
}

/** 화면에 본 마지막 댓글의 시각까지를 읽은 것으로 표시한다 */
export async function markCommentsRead(mapId: string, pinId: string, upTo: string): Promise<void> {
  await apiClient.post(`${commentsPath(mapId, pinId)}/read`, { upTo });
}

export async function listUnread(signal?: AbortSignal): Promise<UnreadPin[]> {
  const response = await apiClient.get<Envelope<UnreadPin[]>>('/comments/unread', { signal });
  return response.data.data;
}
