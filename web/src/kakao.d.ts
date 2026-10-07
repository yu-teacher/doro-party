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

  interface Projection {
    /** 지도 좌표를 지도 컨테이너 안의 픽셀 위치로 바꾼다 */
    containerPointFromCoords(latlng: LatLng): Point;
    coordsFromContainerPoint(point: Point): LatLng;
  }

  interface CustomOverlayOptions {
    position: LatLng;
    content: HTMLElement;
    map?: Map | null;
    xAnchor?: number;
    yAnchor?: number;
    zIndex?: number;
    clickable?: boolean;
  }

  class CustomOverlay {
    constructor(options: CustomOverlayOptions);
    setMap(map: Map | null): void;
  }

  interface CircleOptions {
    center: LatLng;
    /** 반지름(미터) */
    radius: number;
    strokeWeight?: number;
    strokeColor?: string;
    strokeOpacity?: number;
    fillColor?: string;
    fillOpacity?: number;
    map?: Map | null;
    zIndex?: number;
  }

  class Circle {
    constructor(options: CircleOptions);
    setMap(map: Map | null): void;
  }

  class Size {
    constructor(width: number, height: number);
  }

  class Point {
    constructor(x: number, y: number);
    x: number;
    y: number;
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
    setBounds(bounds: LatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void;
    getProjection(): Projection;
    relayout(): void;
  }

  interface MouseEvent {
    latLng: LatLng;
  }

  namespace event {
    function addListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
    /** 이동·확대가 끝났을 때(애니메이션이 끝난 뒤) */
    function addListener(target: Map, type: 'idle', handler: () => void): void;
    function addListener(target: Marker, type: 'click', handler: () => void): void;
    function removeListener(target: Map, type: 'click', handler: (event: MouseEvent) => void): void;
  }
}
