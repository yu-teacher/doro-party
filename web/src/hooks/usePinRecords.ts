import axios from 'axios';
import { useCallback, useEffect, useState } from 'react';
import * as recordsApi from '../api/recordsApi';
import { LIMITS } from '../api/types';
import type { Photo, Visit } from '../api/types';
import { useMapStore } from '../store/mapStore';
import { prepareImage } from '../utils/image';
import { sortVisits } from '../utils/visits';

export interface PinRecords {
  visits: Visit[];
  photos: Photo[];
  loading: boolean;
  /** 방문 기록·사진을 불러오지 못했을 때의 안내 문구 */
  error: string | null;
  addVisit: (visitedOn: string, note: string | null) => Promise<void>;
  removeVisit: (visitId: string) => Promise<void>;
  /** 사진을 줄여서 올린다. 실패한 파일의 사유 목록을 돌려주고(성공한 것은 그대로 반영), 던지지 않는다. */
  uploadPhotos: (files: File[]) => Promise<string[]>;
  removePhoto: (photoId: string) => Promise<void>;
}

/**
 * 열려 있는 핀의 방문 기록과 사진. 바꾸면 핀 목록의 횟수·상태(방문 기록은 가고 싶던 곳을 다녀온 곳으로 바꾼다)도
 * 맞춰야 하므로 변경 뒤에 핀을 다시 불러온다.
 */
export function usePinRecords(mapId: string, pinId: string): PinRecords {
  const reloadPins = useMapStore((state) => state.reloadPins);
  const [visits, setVisits] = useState<Visit[]>([]);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const request = new AbortController();
    setLoading(true);
    setError(null);
    setVisits([]);
    setPhotos([]);
    Promise.all([recordsApi.listVisits(mapId, pinId, request.signal), recordsApi.listPhotos(mapId, pinId, request.signal)])
      .then(([loadedVisits, loadedPhotos]) => {
        setVisits(loadedVisits);
        setPhotos(loadedPhotos);
      })
      .catch((failure: unknown) => {
        if (axios.isCancel(failure)) {
          return;
        }
        console.error('Failed to load pin records', failure);
        setError(failure instanceof Error ? failure.message : '기록을 불러오지 못했어요.');
      })
      .finally(() => {
        if (!request.signal.aborted) {
          setLoading(false);
        }
      });
    return () => request.abort();
  }, [mapId, pinId]);

  const addVisit = useCallback(
    async (visitedOn: string, note: string | null) => {
      const created = await recordsApi.addVisit(mapId, pinId, visitedOn, note);
      setVisits((previous) => sortVisits([created, ...previous]));
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  const removeVisit = useCallback(
    async (visitId: string) => {
      await recordsApi.deleteVisit(mapId, pinId, visitId);
      setVisits((previous) => previous.filter((visit) => visit.id !== visitId));
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  const uploadPhotos = useCallback(
    async (files: File[]) => {
      const failures: string[] = [];
      const room = Math.max(0, LIMITS.photosPerPin - photos.length);
      if (files.length > room) {
        failures.push(`사진은 핀마다 ${LIMITS.photosPerPin}장까지 붙일 수 있어요. ${room}장만 올려요.`);
      }
      for (const file of files.slice(0, room)) {
        try {
          const uploaded = await recordsApi.uploadPhoto(mapId, pinId, await prepareImage(file));
          setPhotos((previous) => [...previous, uploaded]);
        } catch (failure) {
          console.warn('Photo upload failed', failure);
          failures.push(`${file.name}: ${failure instanceof Error ? failure.message : '올리지 못했어요.'}`);
        }
      }
      await reloadPins();
      return failures;
    },
    [mapId, pinId, photos.length, reloadPins],
  );

  const removePhoto = useCallback(
    async (photoId: string) => {
      await recordsApi.deletePhoto(mapId, pinId, photoId);
      setPhotos((previous) => previous.filter((photo) => photo.id !== photoId));
      await reloadPins();
    },
    [mapId, pinId, reloadPins],
  );

  return { visits, photos, loading, error, addVisit, removeVisit, uploadPhotos, removePhoto };
}
