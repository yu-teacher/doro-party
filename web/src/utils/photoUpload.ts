import * as recordsApi from '../api/recordsApi';
import { LIMITS } from '../api/types';
import { prepareImage } from './image';

export interface PhotoUploadOptions {
  /** 이 핀에 더 붙일 수 있는 장수(핀마다 상한이 있다). 넘는 파일은 올리지 않고 안내만 한다. */
  room: number;
  /** 한 장을 올릴 때마다(성공·실패 모두) 불린다: 지금까지 처리한 장수와 전체 장수 */
  onProgress?: (done: number, total: number) => void;
  /** 한 장이 올라갈 때마다 불린다(화면에 바로 반영하려고) */
  onUploaded?: (photo: Awaited<ReturnType<typeof recordsApi.uploadPhoto>>) => void;
}

/**
 * 사진 여러 장을 한 장씩 줄여서 올린다. 한 장이 실패해도 나머지는 계속 올리고, 실패한 사유 목록을 돌려준다(던지지 않는다).
 * 새 핀을 만들 때와 핀 상세에서 사진을 추가할 때 같은 규칙으로 쓴다.
 */
export async function uploadPhotoFiles(mapId: string, pinId: string, files: File[], options: PhotoUploadOptions): Promise<string[]> {
  const failures: string[] = [];
  const room = Math.max(0, options.room);
  if (files.length > room) {
    failures.push(`사진은 핀마다 ${LIMITS.photosPerPin}장까지 붙일 수 있어요. ${room}장만 올려요.`);
  }
  const batch = files.slice(0, room);
  let done = 0;
  for (const file of batch) {
    try {
      // 콜백이 없어도 업로드는 해야 하므로, 콜백 호출식 안에 넣지 않는다(옵셔널 호출은 콜백이 없으면 인자를 평가하지 않는다)
      const uploaded = await recordsApi.uploadPhoto(mapId, pinId, await prepareImage(file));
      options.onUploaded?.(uploaded);
    } catch (failure) {
      console.warn('Photo upload failed', failure);
      failures.push(`${file.name}: ${failure instanceof Error ? failure.message : '올리지 못했어요.'}`);
    }
    done += 1;
    options.onProgress?.(done, batch.length);
  }
  return failures;
}
