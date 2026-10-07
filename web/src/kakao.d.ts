/**
 * 카카오맵 JavaScript SDK 중 이 앱이 쓰는 부분만 선언한다. SDK 는 런타임에 스크립트로 불러오므로 전역 `kakao` 로 존재한다.
 * 쓰는 API 가 늘면 여기에 필요한 만큼만 추가한다.
 */
declare namespace kakao.maps {
  function load(callback: () => void): void;

  class LatLng {
    constructor(latitude: number, longitude: number);
    getLat(): number;
    getLng(): number;
  }

  interface MapOptions {
    center: LatLng;
    level?: number;
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions);
    setCenter(latlng: LatLng): void;
    getCenter(): LatLng;
    setLevel(level: number): void;
    relayout(): void;
  }
}
