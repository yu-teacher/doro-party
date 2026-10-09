/** Vite `base`(/party/). 게이트웨이의 하위 경로 마운트와 같아야 한다. */
const BASE_URL = import.meta.env.BASE_URL;

/** 라우터 basename: 끝의 슬래시를 뗀 경로(/party). */
export const ROUTER_BASENAME = BASE_URL.replace(/\/+$/, '');

/** 백엔드 API 기준 경로(/party/api/v1). 게이트웨이가 /party 를 떼고 백엔드로 전달한다. */
export const API_BASE = `${BASE_URL}api/v1`;

/** 카카오맵 JavaScript 키. 비어 있으면 지도를 그리지 않고 안내를 보여 준다. */
export const KAKAO_MAP_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY?.trim() ?? '';

/** 처음 지도 중심(서울 홍대입구). */
export const DEFAULT_MAP_CENTER = { lat: 37.5563, lng: 126.9236 } as const;
export const DEFAULT_MAP_LEVEL = 4;

/** 핀 상세를 열어 둔 동안 댓글을 다시 불러오는 간격(화면으로 돌아올 때도 불러온다). */
export const COMMENT_REFRESH_MS = 30_000;

/** 새 댓글 배지를 다시 불러오는 간격(화면이 보이는 동안). */
export const UNREAD_REFRESH_MS = 60_000;

/** 앱 새 버전이 있는지 확인하는 간격(화면이 보이는 동안)과, 연달아 확인하지 않는 최소 간격. */
export const UPDATE_CHECK_MS = 15 * 60_000;
export const UPDATE_CHECK_MIN_GAP_MS = 60_000;
