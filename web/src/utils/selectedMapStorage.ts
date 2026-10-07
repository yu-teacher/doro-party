const KEY = 'party.selectedMapId';

/** 마지막으로 본 지도를 기억한다. 저장소를 쓸 수 없는 환경(시크릿 모드 등)에서는 조용히 기억만 포기한다. */
export function readSelectedMapId(): string | null {
  try {
    return window.localStorage.getItem(KEY);
  } catch (error) {
    console.warn('Could not read the remembered map', error);
    return null;
  }
}

export function writeSelectedMapId(mapId: string | null): void {
  try {
    if (mapId === null) {
      window.localStorage.removeItem(KEY);
    } else {
      window.localStorage.setItem(KEY, mapId);
    }
  } catch (error) {
    console.warn('Could not remember the selected map', error);
  }
}
