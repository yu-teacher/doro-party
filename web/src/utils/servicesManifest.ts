/**
 * 도로 허브가 제공하는 서비스 목록(/services.json, 같은 오리진)을 읽는다. 이 앱은 목록을 코드에 두지 않으므로
 * 새 서비스가 생겨도 이 앱을 고치거나 다시 배포할 필요가 없다. 목록을 못 읽으면 "도로 홈" 링크만 보인다.
 * 이 파일은 도로의 다른 웹 앱들과 같은 내용이다(한 곳을 고치면 나머지도 같이 고친다).
 */
export interface LauncherService {
  id: string;
  name: string;
  desc: string;
  /** 같은 오리진의 경로(/로 시작). 다른 오리진·프로토콜 상대 주소는 받지 않는다. */
  href: string;
}

const SERVICES_URL = '/services.json';
const SUPPORTED_VERSION = 1;
const MAX_SERVICES = 20;
const MAX_TEXT_LENGTH = 80;

function isSamePathHref(value: unknown): value is string {
  return typeof value === 'string' && /^\/(?!\/)\S*$/.test(value);
}

function shortText(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed.slice(0, MAX_TEXT_LENGTH) : null;
}

/** 형식이 맞지 않는 항목은 건너뛴다. 전체 형식이 다르면(버전이 다르거나 배열이 아니면) 빈 목록이다. */
export function parseServicesManifest(raw: unknown): LauncherService[] {
  if (typeof raw !== 'object' || raw === null) return [];
  const manifest = raw as { version?: unknown; services?: unknown };
  if (manifest.version !== SUPPORTED_VERSION || !Array.isArray(manifest.services)) return [];

  const services: LauncherService[] = [];
  for (const entry of manifest.services) {
    if (services.length >= MAX_SERVICES) break;
    if (typeof entry !== 'object' || entry === null) continue;
    const item = entry as Record<string, unknown>;
    const id = shortText(item.id);
    const name = shortText(item.name);
    if (id === null || name === null || !isSamePathHref(item.href)) continue;
    services.push({ id, name, desc: shortText(item.desc) ?? '', href: item.href });
  }
  return services;
}

let loaded: LauncherService[] | null = null;

/** 서비스 목록을 읽는다. 성공한 결과만 기억하고, 실패하면 빈 목록을 돌려주되 다음에 다시 시도한다. */
export async function loadServices(fetchImpl: typeof fetch = fetch): Promise<LauncherService[]> {
  if (loaded !== null) return loaded;
  try {
    const response = await fetchImpl(SERVICES_URL, { cache: 'no-cache', credentials: 'same-origin' });
    if (!response.ok) return [];
    const services = parseServicesManifest(await response.json());
    if (services.length > 0) loaded = services;
    return services;
  } catch (failure) {
    console.warn('Failed to load the Doro services list', failure);
    return [];
  }
}

/** 시험에서 기억한 결과를 비운다. */
export function resetServicesCache(): void {
  loaded = null;
}
