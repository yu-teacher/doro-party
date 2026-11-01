import type { Page } from '@playwright/test';

/**
 * 카카오맵 SDK 를 흉내 낸다(실제 키·네트워크 없이 지도 코드가 도는지 보려는 시험용).
 * 마커·묶음(CustomOverlay)은 지도 컨테이너 안의 DOM 으로 그려서, 시험이 화면에 몇 개가 나와 있는지 셀 수 있다.
 * 좌표는 위경도 1도를 10만 픽셀로 펼쳐서 서로 멀리 두면 묶이지 않는다.
 */
const FAKE_SDK = `
(() => {
  const SCALE = 100000;
  class LatLng { constructor(lat, lng) { this.lat = lat; this.lng = lng; } getLat() { return this.lat; } getLng() { return this.lng; } }
  class LatLngBounds { extend() {} }
  class Size { constructor(w, h) { this.w = w; this.h = h; } }
  class Point { constructor(x, y) { this.x = x; this.y = y; } }
  class MarkerImage { constructor(src) { this.src = src; } }
  const handlers = new WeakMap();
  const on = (target, type, fn) => { const m = handlers.get(target) ?? {}; (m[type] ??= []).push(fn); handlers.set(target, m); if (target.el && type === 'click') target.el.addEventListener('click', () => fn()); };
  class Marker {
    constructor(o) { this.el = document.createElement('div'); this.el.className = 'fake-marker'; this.el.dataset.title = o.title ?? ''; this.el.style.cssText = 'width:20px;height:20px;position:absolute;'; this.setMap(o.map ?? null); }
    setMap(map) { if (map) map.container.appendChild(this.el); else this.el.remove(); }
    setPosition() {} setImage() {} setZIndex() {}
  }
  class CustomOverlay {
    constructor(o) { this.el = o.content; this.el.classList.add('fake-cluster'); this.setMap(o.map ?? null); }
    setMap(map) { if (map) map.container.appendChild(this.el); else this.el.remove(); }
    setPosition() {}
  }
  class Circle { constructor() {} setMap() {} setPosition() {} setRadius() {} }
  class Map {
    constructor(container) { this.container = container; this.level = 3; }
    getProjection() { return { containerPointFromCoords: (p) => ({ x: p.lng * SCALE, y: -p.lat * SCALE }), coordsFromContainerPoint: (pt) => new LatLng(-pt.y / SCALE, pt.x / SCALE) }; }
    panTo() {} getCenter() { return new LatLng(37.5, 126.9); } relayout() {}
    getLevel() { return this.level; } setLevel(l) { this.level = l; } setCenter() { window.__fits = (window.__fits ?? 0) + 1; } setBounds() { window.__fits = (window.__fits ?? 0) + 1; }
  }
  window.kakao = { maps: { load: (cb) => cb(), LatLng, LatLngBounds, Size, Point, MarkerImage, Marker, CustomOverlay, Circle, Map, event: { addListener: on } } };
})();
`;

export async function mockKakao(page: Page): Promise<void> {
  await page.route('https://dapi.kakao.com/**', (route) => route.fulfill({ status: 200, contentType: 'application/javascript', body: FAKE_SDK }));
}
