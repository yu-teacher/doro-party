import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadServices, parseServicesManifest, resetServicesCache } from './servicesManifest';

const valid = {
  version: 1,
  services: [
    { id: 'blog', name: 'DORO.log', desc: '개발자 블로그', href: '/blog/' },
    { id: 'party', name: '도로 파티', desc: '지도에 핀 모으기', href: '/party/' },
  ],
};

afterEach(() => {
  resetServicesCache();
  vi.restoreAllMocks();
});

describe('parseServicesManifest', () => {
  it('올바른 목록은 그대로 읽는다', () => {
    expect(parseServicesManifest(valid)).toEqual(valid.services);
  });

  it('형식이 다르면 빈 목록이다(버전이 다름, 배열이 아님, 객체가 아님)', () => {
    expect(parseServicesManifest({ ...valid, version: 2 })).toEqual([]);
    expect(parseServicesManifest({ version: 1, services: 'x' })).toEqual([]);
    expect(parseServicesManifest(null)).toEqual([]);
    expect(parseServicesManifest('services')).toEqual([]);
  });

  it('다른 오리진·프로토콜 상대·스크립트 주소는 건너뛴다', () => {
    const services = [
      { id: 'a', name: 'A', href: 'https://evil.example/' },
      { id: 'b', name: 'B', href: '//evil.example/' },
      { id: 'c', name: 'C', href: 'javascript:alert(1)' },
      { id: 'd', name: 'D', href: '/ok/' },
    ];
    expect(parseServicesManifest({ version: 1, services }).map((s) => s.id)).toEqual(['d']);
  });

  it('id·이름이 비었거나 형식이 틀린 항목은 건너뛰고 긴 글자는 자른다', () => {
    const long = 'x'.repeat(200);
    const parsed = parseServicesManifest({
      version: 1,
      services: [{ id: '', name: 'A', href: '/a/' }, { id: 'b', href: '/b/' }, 7, null, { id: 'c', name: long, href: '/c/' }],
    });
    expect(parsed).toHaveLength(1);
    expect(parsed[0].name).toHaveLength(80);
    expect(parsed[0].desc).toBe('');
  });

  it('항목은 20개까지만 읽는다', () => {
    const services = Array.from({ length: 30 }, (_, i) => ({ id: `s${i}`, name: `S${i}`, href: `/s${i}/` }));
    expect(parseServicesManifest({ version: 1, services })).toHaveLength(20);
  });
});

describe('loadServices', () => {
  const okFetch = () => Promise.resolve({ ok: true, json: () => Promise.resolve(valid) } as Response);

  it('성공하면 목록을 돌려주고 한 번만 읽는다', async () => {
    const fetchMock = vi.fn(okFetch);
    expect(await loadServices(fetchMock as unknown as typeof fetch)).toEqual(valid.services);
    expect(await loadServices(fetchMock as unknown as typeof fetch)).toEqual(valid.services);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/services.json', { cache: 'no-cache', credentials: 'same-origin' });
  });

  it('실패(네트워크 오류·HTTP 오류·잘못된 JSON)하면 빈 목록이고 다음에 다시 시도한다', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const failing = vi.fn().mockRejectedValue(new Error('offline'));
    expect(await loadServices(failing as unknown as typeof fetch)).toEqual([]);
    const notFound = vi.fn().mockResolvedValue({ ok: false } as Response);
    expect(await loadServices(notFound as unknown as typeof fetch)).toEqual([]);
    const retry = vi.fn(okFetch);
    expect(await loadServices(retry as unknown as typeof fetch)).toEqual(valid.services);
    expect(retry).toHaveBeenCalledTimes(1);
  });
});
