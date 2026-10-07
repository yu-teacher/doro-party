import { LIMITS } from '../api/types';

const JPEG_QUALITY = 0.82;

/** 비율을 지키며 긴 변이 maxEdge 를 넘지 않게 줄인 크기. 작은 사진은 키우지 않는다. */
export function fitWithin(width: number, height: number, maxEdge: number): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= maxEdge) {
    return { width, height };
  }
  const scale = maxEdge / longest;
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/**
 * 올리기 전에 사진을 줄여 JPEG 로 바꾼다(전송량·저장 공간을 아끼고, 어떤 형식이든 서버가 받는 JPEG 로 통일).
 * 촬영 방향(EXIF)을 반영하고, 투명한 PNG 는 흰 배경에 그린다. 읽을 수 없으면 이유를 담은 오류를 던진다.
 */
export async function prepareImage(file: File): Promise<Blob> {
  if (!file.type.startsWith('image/')) {
    throw new Error('사진 파일만 올릴 수 있어요.');
  }
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch (error) {
    console.warn('Could not decode the selected image', error);
    throw new Error('이 사진을 읽을 수 없어요. 다른 사진으로 시도해 주세요.');
  }
  try {
    const { width, height } = fitWithin(bitmap.width, bitmap.height, LIMITS.photoMaxEdge);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('이 브라우저에서는 사진을 줄일 수 없어요.');
    }
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    if (!blob) {
      throw new Error('사진을 변환하지 못했어요.');
    }
    return blob;
  } finally {
    bitmap.close();
  }
}
