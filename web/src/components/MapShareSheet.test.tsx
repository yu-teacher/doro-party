import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as mapsApi from '../api/mapsApi';
import * as socialApi from '../api/socialApi';
import type { FriendAccess, PartyMap } from '../api/types';
import { useMapStore } from '../store/mapStore';
import MapShareSheet from './MapShareSheet';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock('../api/mapsApi');
vi.mock('../api/socialApi');
const maps = vi.mocked(mapsApi);
const social = vi.mocked(socialApi);

function myMap(friendAccess: FriendAccess): PartyMap {
  return {
    id: 'm1', name: '맛집', description: null, ownerId: 'u', ownerNickname: '나', ownerColor: '#14B8A6', mine: true, role: 'OWNER',
    viaGroups: [], friendAccess, pinCount: 0, createdAt: '', updatedAt: '',
  };
}

/** 스토어에 있는 지도를 그대로 보여 주는 시트(공개 범위를 바꾸면 스토어가 갱신되어 다시 그려진다). */
function Harness() {
  const map = useMapStore((state) => state.maps[0]);
  return <MapShareSheet map={map} onClose={() => undefined} />;
}

let container: HTMLDivElement;
let root: Root;

async function render() {
  await act(async () => {
    root.render(<Harness />);
  });
}

const radio = (label: string) => Array.from(container.querySelectorAll('[role="radio"]')).find((button) => button.textContent === label) as HTMLButtonElement;
const checkedLabel = () => container.querySelector('[role="radio"][aria-checked="true"]')?.textContent;

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  vi.resetAllMocks();
  social.listShares.mockResolvedValue([]);
  social.getFriends.mockResolvedValue({ friends: [], incoming: [], outgoing: [] });
  social.listGroups.mockResolvedValue([]);
  social.listGroupsOfMap.mockResolvedValue([]);
  useMapStore.getState().reset();
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('MapShareSheet: 친구 전체에게 공개', () => {
  it('현재 공개 범위가 선택돼 보인다', async () => {
    useMapStore.setState({ maps: [myMap('VIEWER')] });
    await render();
    expect(checkedLabel()).toBe('친구 전체 보기');
    expect(container.textContent).toContain('앞으로 생길 친구 모두가 볼 수 있어요');
  });

  it('범위를 고르면 서버에 보내고, 응답으로 목록과 화면이 바뀐다', async () => {
    useMapStore.setState({ maps: [myMap('NONE')] });
    maps.setFriendAccess.mockResolvedValue(myMap('VIEWER'));
    await render();
    expect(checkedLabel()).toBe('비공개');

    await act(async () => {
      radio('친구 전체 보기').click();
    });

    expect(maps.setFriendAccess).toHaveBeenCalledWith('m1', 'VIEWER');
    expect(useMapStore.getState().maps[0].friendAccess).toBe('VIEWER');
    expect(checkedLabel()).toBe('친구 전체 보기');
  });

  it('편집을 고르면 친구 모두가 고칠 수 있다는 경고를 보여 준다', async () => {
    useMapStore.setState({ maps: [myMap('VIEWER')] });
    maps.setFriendAccess.mockResolvedValue(myMap('EDITOR'));
    await render();

    await act(async () => {
      radio('친구 전체 편집').click();
    });

    expect(maps.setFriendAccess).toHaveBeenCalledWith('m1', 'EDITOR');
    expect(container.textContent).toContain('친구 모두가 이 지도에 핀을 꽂고 고칠 수 있어요');
    expect(container.textContent).toContain('지도 삭제와 이름 변경은 나만 할 수 있어요');
  });

  it('이미 선택된 범위를 다시 누르면 서버에 보내지 않는다', async () => {
    useMapStore.setState({ maps: [myMap('VIEWER')] });
    await render();
    await act(async () => {
      radio('친구 전체 보기').click();
    });
    expect(maps.setFriendAccess).not.toHaveBeenCalled();
  });

  it('바꾸지 못하면 이유를 알리고 선택은 그대로 둔다', async () => {
    useMapStore.setState({ maps: [myMap('NONE')] });
    maps.setFriendAccess.mockRejectedValue(new Error('권한이 없어요'));
    await render();

    await act(async () => {
      radio('친구 전체 편집').click();
    });

    expect(container.querySelector('[role="alert"]')?.textContent).toBe('권한이 없어요');
    expect(checkedLabel()).toBe('비공개');
    expect(useMapStore.getState().maps[0].friendAccess).toBe('NONE');
  });
});
