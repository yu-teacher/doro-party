/** Geolocation 실패 코드별 안내 문구 */
export const LOCATE_ERRORS: Readonly<Record<number, string>> = {
  1: '위치 권한이 꺼져 있어요. 브라우저 설정에서 허용해 주세요.',
  2: '현재 위치를 알 수 없어요.',
  3: '위치를 확인하는 데 시간이 너무 오래 걸려요.',
};

export const LOCATE_UNSUPPORTED = '이 브라우저는 위치 확인을 지원하지 않아요.';
export const LOCATE_FAILED = '현재 위치를 확인하지 못했어요.';

/** 위치 요청 제한 시간(ms) */
export const LOCATE_TIMEOUT_MS = 10_000;

export function locateErrorMessage(code: number): string {
  return LOCATE_ERRORS[code] ?? LOCATE_FAILED;
}
