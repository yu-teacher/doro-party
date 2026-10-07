import { API_BASE } from '../config';
import { apiClient } from './client';
import type { Photo, PrivateNote, Visit } from './types';

/** 서버의 공통 응답 봉투 { success, data, timestamp } */
interface Envelope<T> {
  success: boolean;
  data: T;
}

const pinPath = (mapId: string, pinId: string) => `/maps/${mapId}/pins/${pinId}`;

// ---------------------------------------------------------------- 방문 기록

export async function listVisits(mapId: string, pinId: string, signal?: AbortSignal): Promise<Visit[]> {
  const response = await apiClient.get<Envelope<Visit[]>>(`${pinPath(mapId, pinId)}/visits`, { signal });
  return response.data.data;
}

export async function addVisit(mapId: string, pinId: string, visitedOn: string, note: string | null): Promise<Visit> {
  const response = await apiClient.post<Envelope<Visit>>(`${pinPath(mapId, pinId)}/visits`, { visitedOn, note });
  return response.data.data;
}

export async function deleteVisit(mapId: string, pinId: string, visitId: string): Promise<void> {
  await apiClient.delete(`${pinPath(mapId, pinId)}/visits/${visitId}`);
}

// ---------------------------------------------------------------- 사적 메모

/** 이 지도에서 내가 남긴 사적 메모 전체 */
export async function listPrivateNotes(mapId: string, signal?: AbortSignal): Promise<PrivateNote[]> {
  const response = await apiClient.get<Envelope<PrivateNote[]>>(`/maps/${mapId}/private-notes`, { signal });
  return response.data.data;
}

export async function savePrivateNote(mapId: string, pinId: string, body: string): Promise<PrivateNote> {
  const response = await apiClient.put<Envelope<PrivateNote>>(`${pinPath(mapId, pinId)}/private-note`, { body });
  return response.data.data;
}

export async function deletePrivateNote(mapId: string, pinId: string): Promise<void> {
  await apiClient.delete(`${pinPath(mapId, pinId)}/private-note`);
}

// ---------------------------------------------------------------- 사진

export async function listPhotos(mapId: string, pinId: string, signal?: AbortSignal): Promise<Photo[]> {
  const response = await apiClient.get<Envelope<Photo[]>>(`${pinPath(mapId, pinId)}/photos`, { signal });
  return response.data.data;
}

/** 사진을 올린다. 파일 이름은 의미가 없고, 서버는 내용으로 형식을 판별한다. */
export async function uploadPhoto(mapId: string, pinId: string, file: Blob): Promise<Photo> {
  const form = new FormData();
  form.append('file', file, 'photo.jpg');
  const response = await apiClient.post<Envelope<Photo>>(`${pinPath(mapId, pinId)}/photos`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function deletePhoto(mapId: string, pinId: string, photoId: string): Promise<void> {
  await apiClient.delete(`${pinPath(mapId, pinId)}/photos/${photoId}`);
}

/** 사진 내용 주소. 같은 사이트 요청이라 세션 쿠키가 붙고, 서버가 지도 권한을 확인한 뒤에만 내려준다. */
export function photoUrl(mapId: string, pinId: string, photoId: string): string {
  return `${API_BASE}${pinPath(mapId, pinId)}/photos/${photoId}/content`;
}
