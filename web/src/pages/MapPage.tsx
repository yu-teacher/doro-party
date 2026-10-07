import { ListOrdered, LocateFixed, MapPinPlus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { Pin, RecommendedPlace } from '../api/types';
import ClusterSheet from '../components/ClusterSheet';
import FilterBar from '../components/FilterBar';
import MapFormSheet from '../components/MapFormSheet';
import MapNotice from '../components/MapNotice';
import MapShareSheet from '../components/MapShareSheet';
import MapSwitcher from '../components/MapSwitcher';
import OverlayBar from '../components/OverlayBar';
import OverlayMapsSheet from '../components/OverlayMapsSheet';
import PinSheet from '../components/PinSheet';
import NearbySheet from '../components/NearbySheet';
import PlacementBar from '../components/PlacementBar';
import RecommendationsSheet from '../components/RecommendationsSheet';
import SharedMapSheet from '../components/SharedMapSheet';
import { toHeatSpots } from '../map/heat';
import PartyMapView from '../map/PartyMapView';
import type { LatLngLiteral, PartyMapHandle } from '../map/PartyMapView';
import { buildLoginUrl, useAuthStore } from '../store/authStore';
import { filterPins, useMapStore } from '../store/mapStore';
import { readRememberedOverlayMaps, useOverlayStore } from '../store/overlayStore';
import { LOCATE_TIMEOUT_MS, LOCATE_UNSUPPORTED, locateErrorMessage } from '../utils/geolocation';
import { canPlacePins } from '../utils/mapRole';
import { authorsOf, defaultOverlayMapIds, withoutHiddenAuthors } from '../utils/overlaySelection';
import { collectTags } from '../utils/tags';

type Sheet =
  | { kind: 'none' }
  | { kind: 'new-pin'; lat: number; lng: number }
  | { kind: 'pin'; pinId: string }
  | { kind: 'new-map' }
  | { kind: 'map-settings' }
  | { kind: 'map-share' }
  | { kind: 'shared-info' }
  | { kind: 'overlay-maps' }
  | { kind: 'recommendations' }
  | { kind: 'cluster'; pins: Pin[] }
  | { kind: 'nearby' };

const NO_SHEET: Sheet = { kind: 'none' };
export default function MapPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const { maps, mapsLoaded, selectedMapId, pins, pinsLoading, error, statusFilter, tagFilter } = useMapStore();
  const { loadMaps, selectMap, setStatusFilter, setTagFilter, reset } = useMapStore.getState();
  const overlay = useOverlayStore();
  const location = useLocation();
  const navigate = useNavigate();
  const [sheet, setSheet] = useState<Sheet>(NO_SHEET);
  const [panTo, setPanTo] = useState<(LatLngLiteral & { nonce: number }) | null>(null);
  const [locateError, setLocateError] = useState<string | null>(null);
  /** 핀을 꽂으려고 지도에서 고른 위치(아직 입력 시트는 열지 않았다) */
  const [placing, setPlacing] = useState<LatLngLiteral | null>(null);
  const mapView = useRef<PartyMapHandle>(null);
  const sheetKind = useRef<Sheet['kind']>('none');
  useEffect(() => {
    sheetKind.current = sheet.kind;
  });

  useEffect(() => {
    if (isAuthenticated) {
      void loadMaps();
    } else {
      reset();
      useOverlayStore.getState().close();
      setSheet(NO_SHEET);
      setPlacing(null);
    }
  }, [isAuthenticated, loadMaps, reset]);

  // 다른 지도로 바꾸면 열려 있던 시트(이전 지도의 핀 등)는 닫는다
  useEffect(() => {
    setSheet(NO_SHEET);
    setPlacing(null);
  }, [selectedMapId]);

  // 모임 화면의 "오늘 어디 갈까?" 로 들어오면 겹쳐보기가 켜진 채 추천 목록부터 연다(한 번만)
  const wantsRecommendations = (location.state as { recommend?: boolean } | null)?.recommend === true;
  useEffect(() => {
    if (wantsRecommendations && overlay.active) {
      setSheet({ kind: 'recommendations' });
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [wantsRecommendations, overlay.active, navigate, location.pathname]);

  const overlayActive = overlay.active;
  const visiblePins = useMemo(() => filterPins(pins, statusFilter, tagFilter), [pins, statusFilter, tagFilter]);
  const overlayVisiblePins = useMemo(() => withoutHiddenAuthors(overlay.pins, overlay.hiddenAuthors), [overlay.pins, overlay.hiddenAuthors]);
  const overlayAuthors = useMemo(() => authorsOf(overlay.pins), [overlay.pins]);
  const heat = useMemo(() => (overlayActive && overlay.heatmap ? toHeatSpots(overlay.recommendations) : null), [overlayActive, overlay.heatmap, overlay.recommendations]);
  const mapNames = useMemo(() => new Map(maps.map((map) => [map.id, map.name])), [maps]);
  const tags = useMemo(() => collectTags(pins), [pins]);
  const selectedMap = maps.find((map) => map.id === selectedMapId) ?? null;
  const canPlace = !overlayActive && canPlacePins(selectedMap?.role);
  const pinSource = overlayActive ? overlay.pins : pins;
  const openedPin = sheet.kind === 'pin' ? pinSource.find((pin) => pin.id === sheet.pinId) ?? null : null;
  const closeSheet = useCallback(() => setSheet(NO_SHEET), []);

  const openOverlay = () => {
    const ids = defaultOverlayMapIds(maps.map((map) => map.id), readRememberedOverlayMaps());
    setSheet(NO_SHEET);
    setPlacing(null);
    void useOverlayStore.getState().open(ids);
  };

  const closeOverlay = () => {
    useOverlayStore.getState().close();
    setSheet(NO_SHEET);
  };

  /** 겹쳐보기에서 본 핀을 고치려면 그 핀이 속한 지도를 열어 거기서 한다. */
  const openPinInItsMap = async (pin: Pin) => {
    useOverlayStore.getState().close();
    await selectMap(pin.mapId);
    setSheet({ kind: 'pin', pinId: pin.id });
  };

  /** 가까운 핀을 누르면 그 핀이 속한 지도를 열고, 지도를 그 핀으로 옮겨 상세를 보여 준다. */
  const pickNearby = (pin: Pin) => {
    setPanTo({ lat: pin.lat, lng: pin.lng, nonce: Date.now() });
    void openPinInItsMap(pin);
  };

  /** 추천 장소를 누르면 그 장소로 지도를 옮기고, 거기 모인 핀들을 목록으로 보여 준다. */
  const pickRecommendation = (place: RecommendedPlace) => {
    const ids = new Set(place.pinIds);
    setPanTo({ lat: place.lat, lng: place.lng, nonce: Date.now() });
    setSheet({ kind: 'cluster', pins: overlay.pins.filter((pin) => ids.has(pin.id)) });
  };

  const onMapClick = useCallback((position: LatLngLiteral) => {
    if (!useAuthStore.getState().isAuthenticated) {
      return;
    }
    if (useOverlayStore.getState().active) {
      // 겹쳐보기에서는 핀을 꽂지 않는다. 열려 있는 핀·묶음 목록만 닫는다.
      if (sheetKind.current === 'pin' || sheetKind.current === 'cluster') {
        setSheet(NO_SHEET);
      }
      return;
    }
    if (useMapStore.getState().selectedMapId === null) {
      setSheet({ kind: 'new-map' });
      return;
    }
    // 보기 전용 지도에서는 핀을 꽂지 않는다(서버도 막지만 헛된 입력 창을 열지 않는다)
    const state = useMapStore.getState();
    if (!canPlacePins(state.maps.find((map) => map.id === state.selectedMapId)?.role) && sheetKind.current === 'none') {
      return;
    }
    // 지도를 눌렀을 때: 열려 있는 핀 상세는 닫고, 입력 중인 핀은 내용을 둔 채 위치만 옮기고, 다른 입력 시트는 건드리지 않는다.
    // (시트가 지도를 가리고 있을 때 눌러서 입력 중이던 내용을 잃지 않게 한다)
    switch (sheetKind.current) {
      case 'none':
        setPlacing(position);
        break;
      case 'pin':
        setSheet(NO_SHEET);
        break;
      case 'new-pin':
        setSheet({ kind: 'new-pin', ...position });
        break;
      default:
        break;
    }
  }, []);

  const onPinClick = useCallback((pinId: string) => setSheet({ kind: 'pin', pinId }), []);
  const onClusterOpen = useCallback((clusterPins: Pin[]) => setSheet({ kind: 'cluster', pins: clusterPins }), []);

  const locate = () => {
    setLocateError(null);
    if (!('geolocation' in navigator)) {
      setLocateError(LOCATE_UNSUPPORTED);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => setPanTo({ lat: position.coords.latitude, lng: position.coords.longitude, nonce: Date.now() }),
      (failure) => setLocateError(locateErrorMessage(failure.code)),
      { enableHighAccuracy: true, timeout: LOCATE_TIMEOUT_MS },
    );
  };

  const draft = sheet.kind === 'new-pin' ? { lat: sheet.lat, lng: sheet.lng } : placing;

  const placeAtCenter = () => {
    const center = mapView.current?.getCenter();
    if (center) {
      setPlacing(center);
    }
  };

  const confirmPlacing = () => {
    if (placing) {
      setSheet({ kind: 'new-pin', ...placing });
      setPlacing(null);
    }
  };

  const mapPins = !isAuthenticated ? [] : overlayActive ? overlayVisiblePins : visiblePins;
  const fitKey = !isAuthenticated ? null : overlayActive ? (overlay.loading ? null : `overlay:${overlay.loadCount}`) : pinsLoading ? null : selectedMapId;

  return (
    <div className="relative h-full overflow-hidden">
      <PartyMapView
        ref={mapView}
        pins={mapPins}
        selectedPinId={sheet.kind === 'pin' ? sheet.pinId : null}
        draft={overlayActive ? null : draft}
        fitKey={fitKey}
        panTo={panTo}
        colorMode={overlayActive ? 'author' : 'status'}
        heat={heat}
        onMapClick={onMapClick}
        onPinClick={onPinClick}
        onClusterOpen={onClusterOpen}
      />

      {isAuthenticated && mapsLoaded && (
        <div className="absolute inset-x-3 top-3 z-10 mx-auto flex max-w-md flex-col gap-2">
          {overlayActive ? (
            <OverlayBar
              mapCount={overlay.mapIds.length}
              pinCount={overlayVisiblePins.length}
              loading={overlay.loading}
              error={overlay.error}
              authors={overlayAuthors}
              hiddenAuthors={overlay.hiddenAuthors}
              onPickMaps={() => setSheet({ kind: 'overlay-maps' })}
              onOpenRecommendations={() => setSheet({ kind: 'recommendations' })}
              heatmap={overlay.heatmap}
              onToggleHeatmap={overlay.toggleHeatmap}
              onToggleAuthor={overlay.toggleAuthor}
              onClose={closeOverlay}
            />
          ) : (
            <>
              <MapSwitcher
                maps={maps}
                selectedMapId={selectedMapId}
                onSelect={(mapId) => void selectMap(mapId)}
                onCreate={() => setSheet({ kind: 'new-map' })}
                onSettings={() => setSheet({ kind: selectedMap?.role === 'OWNER' ? 'map-settings' : 'shared-info' })}
                onOverlay={openOverlay}
              />
              {pins.length > 0 && (
                <FilterBar statusFilter={statusFilter} tagFilter={tagFilter} tags={tags} onStatusChange={setStatusFilter} onTagChange={setTagFilter} />
              )}
            </>
          )}
        </div>
      )}

      {!isAuthenticated && (
        <MapNotice title="내 지도를 만들어 보세요">
          <p className="mb-3">로그인하면 가고 싶은 곳과 다녀온 곳을 지도에 핀으로 꽂아 기록할 수 있어요.</p>
          <a href={buildLoginUrl('/')} className="inline-block rounded-lg bg-teal-500 px-4 py-2 font-semibold text-slate-950 hover:bg-teal-400">로그인</a>
        </MapNotice>
      )}

      {isAuthenticated && mapsLoaded && maps.length === 0 && !error && sheet.kind === 'none' && (
        <MapNotice title="첫 지도를 만들어 보세요">
          <p className="mb-3">「홍대 맛집」, 「일본 여행」처럼 주제별로 지도를 만들고 핀을 꽂아요.</p>
          <button type="button" onClick={() => setSheet({ kind: 'new-map' })} className="rounded-lg bg-teal-500 px-4 py-2 font-semibold text-slate-950 hover:bg-teal-400">
            지도 만들기
          </button>
        </MapNotice>
      )}

      {isAuthenticated && !overlayActive && selectedMap && !pinsLoading && pins.length === 0 && sheet.kind === 'none' && (
        <MapNotice title="아직 핀이 없어요">{canPlace ? '지도를 눌러 첫 핀을 꽂아 보세요.' : '이 지도에는 아직 핀이 없어요.'}</MapNotice>
      )}

      {isAuthenticated && overlayActive && !overlay.loading && !overlay.error && overlay.pins.length === 0 && sheet.kind === 'none' && (
        <MapNotice title="겹칠 핀이 없어요">고른 지도에 아직 핀이 없어요. 위의 지도 고르기에서 다른 지도를 골라 보세요.</MapNotice>
      )}

      {error && (
        <div role="alert" className="absolute inset-x-3 bottom-4 z-10 mx-auto flex max-w-md items-center justify-between gap-3 rounded-xl bg-rose-500/90 px-4 py-3 text-sm text-white shadow-lg">
          <span>{error}</span>
          <button type="button" onClick={() => void loadMaps()} className="shrink-0 font-semibold underline">다시 시도</button>
        </div>
      )}

      {locateError && (
        <div role="status" className="absolute inset-x-3 bottom-20 z-10 mx-auto max-w-md rounded-xl bg-slate-900/95 px-4 py-2.5 text-sm text-slate-200 shadow-lg ring-1 ring-slate-700">
          {locateError}
        </div>
      )}

      {placing && sheet.kind === 'none' && <PlacementBar onConfirm={confirmPlacing} onCancel={() => setPlacing(null)} />}

      <div className={`absolute right-3 z-10 flex flex-col gap-2 ${placing ? 'bottom-32' : 'bottom-4'}`}>
        {isAuthenticated && canPlace && sheet.kind === 'none' && !placing && (
          <button
            type="button"
            onClick={placeAtCenter}
            className="rounded-full bg-teal-500 p-3 text-slate-950 shadow-lg hover:bg-teal-400"
            aria-label="지도 중앙에 핀 꽂기"
          >
            <MapPinPlus size={20} />
          </button>
        )}
        {isAuthenticated && maps.length > 0 && (
          <button
            type="button"
            onClick={() => {
              setPlacing(null);
              setSheet({ kind: 'nearby' });
            }}
            className="rounded-full bg-slate-900/95 p-3 text-slate-100 shadow-lg ring-1 ring-slate-700 hover:bg-slate-800"
            aria-label="내 주변 핀 보기"
          >
            <ListOrdered size={20} />
          </button>
        )}
        <button
          type="button"
          onClick={locate}
          className="rounded-full bg-slate-900/95 p-3 text-slate-100 shadow-lg ring-1 ring-slate-700 hover:bg-slate-800"
          aria-label="내 위치로 이동"
        >
          <LocateFixed size={20} />
        </button>
      </div>

      {sheet.kind === 'new-pin' && (
        <PinSheet mode="create" lat={sheet.lat} lng={sheet.lng} onCreated={(pin) => setSheet({ kind: 'pin', pinId: pin.id })} onClose={closeSheet} />
      )}
      {sheet.kind === 'pin' && openedPin && (
        <PinSheet mode="view" pin={openedPin} onClose={closeSheet} readOnly={overlayActive} onOpenInMap={overlayActive ? (pin) => void openPinInItsMap(pin) : undefined} />
      )}
      {sheet.kind === 'new-map' && <MapFormSheet map={null} onClose={closeSheet} />}
      {sheet.kind === 'map-settings' && selectedMap && (
        <MapFormSheet map={selectedMap} onClose={closeSheet} onOpenShare={() => setSheet({ kind: 'map-share' })} />
      )}
      {sheet.kind === 'map-share' && selectedMap && <MapShareSheet map={selectedMap} onClose={closeSheet} />}
      {sheet.kind === 'shared-info' && selectedMap && <SharedMapSheet map={selectedMap} onClose={closeSheet} />}
      {sheet.kind === 'overlay-maps' && (
        <OverlayMapsSheet
          maps={maps}
          selectedIds={overlay.mapIds}
          onApply={(ids) => {
            setSheet(NO_SHEET);
            void useOverlayStore.getState().setMaps(ids);
          }}
          onClose={closeSheet}
        />
      )}
      {sheet.kind === 'recommendations' && (
        <RecommendationsSheet
          places={overlay.recommendations}
          loading={overlay.recsLoading}
          error={overlay.recsError}
          minPeople={overlay.minPeople}
          excludedCount={overlay.hiddenAuthors.length}
          heatmap={overlay.heatmap}
          onMinPeople={(value) => void overlay.setMinPeople(value)}
          onToggleHeatmap={overlay.toggleHeatmap}
          onPick={pickRecommendation}
          onClose={closeSheet}
        />
      )}
      {sheet.kind === 'nearby' && <NearbySheet maps={maps} onPick={pickNearby} onClose={closeSheet} />}
      {sheet.kind === 'cluster' && (
        <ClusterSheet pins={sheet.pins} mapNames={mapNames} onPick={(pin) => setSheet({ kind: 'pin', pinId: pin.id })} onClose={closeSheet} />
      )}
    </div>
  );
}
