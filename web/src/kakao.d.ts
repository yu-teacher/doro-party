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

  class LatLngBounds {
    constructor();
    extend(latlng: LatLng): void;
  }

  class Size {
    constructor(width: number, height: number);
  }

  class Point {
    constructor(x: number, y: number);
  }

  interface MarkerImageOptions {
    offset?: Point;
  }

  class MarkerImage {
    constructor(src: string, size: Size, options?: MarkerImageOptions);
  }

  interface MarkerOptions {
    position: LatLng;
    image?: MarkerImage;
    map?: Map | null;
    title?: string;
    zIndex?: number;
  }

  class Marker {
    constructor(options: MarkerOptions);
    setMap(map: Map | null): void;
    setPosition(position: LatLng): void;
    setImage(image: MarkerImage): void;
    setZIndex(zIndex: number): void;
  }

  interface MapOptions {
    center: LatLng;
    level?: number;
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions);
    setCenter(latlng: LatLng): void;
    panTo(latlng: LatLng): void;
    getCenter(): LatLng;
    setLevel(level: number): void;
    getLevel(): number;
    setBounds(bounds: LatLngBounds): void;
    relayout(): void;
  }

  interface MouseEvent {
    latLng: LatLng;
  }

  namespace event {
    function addListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
    function addListener(target: Marker, type: 'click', handler: () => void): void;
    function removeListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
  }
}
