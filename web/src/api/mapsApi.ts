import { apiClient } from './client';
import type { MapInput, PartyMap, Pin, PinInput } from './types';

/** 서버의 공통 응답 봉투 { success, data, timestamp } */
interface Envelope<T> {
  success: boolean;
  data: T;
}

export async function listMyMaps(signal?: AbortSignal): Promise<PartyMap[]> {
  const response = await apiClient.get<Envelope<PartyMap[]>>('/maps', { signal });
  return response.data.data;
}

export async function createMap(input: MapInput): Promise<PartyMap> {
  const response = await apiClient.post<Envelope<PartyMap>>('/maps', input);
  return response.data.data;
}

export async function updateMap(mapId: string, input: MapInput): Promise<PartyMap> {
  const response = await apiClient.put<Envelope<PartyMap>>(`/maps/${mapId}`, input);
  return response.data.data;
}

export async function deleteMap(mapId: string): Promise<void> {
  await apiClient.delete(`/maps/${mapId}`);
}

export async function listPins(mapId: string, signal?: AbortSignal): Promise<Pin[]> {
  const response = await apiClient.get<Envelope<Pin[]>>(`/maps/${mapId}/pins`, { signal });
  return response.data.data;
}

export async function createPin(mapId: string, input: PinInput): Promise<Pin> {
  const response = await apiClient.post<Envelope<Pin>>(`/maps/${mapId}/pins`, input);
  return response.data.data;
}

export async function updatePin(mapId: string, pinId: string, input: PinInput): Promise<Pin> {
  const response = await apiClient.put<Envelope<Pin>>(`/maps/${mapId}/pins/${pinId}`, input);
  return response.data.data;
}

export async function deletePin(mapId: string, pinId: string): Promise<void> {
  await apiClient.delete(`/maps/${mapId}/pins/${pinId}`);
}
