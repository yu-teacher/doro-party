import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as socialApi from '../api/socialApi';
import type { FriendMapsOverview, PartyMap } from '../api/types';
import { useAuthStore } from '../store/authStore';
import { useMapStore } from '../store/mapStore';
import FriendMapsPage from './FriendMapsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock('../api/socialApi');
const social = vi.mocked(socialApi);

function friendMap(id: string, name: string, role: 'VIEWER' | 'EDITOR', pinCount: number): PartyMap {
  return {
    id, name, description: id === 'm1' ? '홍대 근처' : null, ownerId: 'f1', ownerNickname: '앨리스', ownerColor: '#E4572E', mine: false, role,
    viaGroups: [], friendAccess: role, pinCount, createdAt: '', updatedAt: '',
  };
}

const OVERVIEW: FriendMapsOverview = {
  friends: [{
    friend: { id: 'f1', username: 'alice', nickname: '앨리스', color: '#E4572E' },
    maps: [friendMap('m1', '맛집', 'VIEWER', 12), friendMap('m2', '카페', 'EDITOR', 0)],
  }],
};

let container: HTMLDivElement;
let root: Root;

async function render() {
  await act(async () => {
    root.render(
      <MemoryRouter initialEntries={['/friends/maps']}>
        <Routes>
          <Route path="/" element={<p>HOME</p>} />
          <Route path="/friends" element={<p>FRIENDS</p>} />
          <Route path="/friends/maps" element={<FriendMapsPage />} />
        </Routes>
      </MemoryRouter>,
    );
  });
}

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  vi.resetAllMocks();
  useAuthStore.setState({ user: { id: 'me', username: 'me', nickname: '나', color: '#14B8A6' } as never, isAuthenticated: true });
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  useAuthStore.setState({ user: null, isAuthenticated: false });
});

describe('FriendMapsPage', () => {
  it('친구별로 공개된 지도를 보여 주고 내 권한과 핀 수를 알려 준다', async () => {
    social.getFriendMaps.mockResolvedValue(OVERVIEW);
    await render();

    expect(container.querySelector('section[aria-label="앨리스님의 공개 지도"]')).not.toBeNull();
    const text = container.textContent ?? '';
    expect(text).toContain('맛집');
    expect(text).toContain('홍대 근처');
    expect(text).toContain('보기만');
    expect(text).toContain('핀 12개');
    expect(text).toContain('카페');
    expect(text).toContain('핀도 꽂기 가능');
    expect(text).toContain('핀 0개');
  });

  it('지도를 누르면 그 지도를 열고 내 지도 화면으로 이동한다', async () => {
    social.getFriendMaps.mockResolvedValue(OVERVIEW);
    const openMap = vi.fn().mockResolvedValue(undefined);
    useMapStore.setState({ openMap });
    await render();

    const card = Array.from(container.querySelectorAll('button')).find((button) => button.textContent?.includes('맛집')) as HTMLButtonElement;
    await act(async () => {
      card.click();
    });

    expect(openMap).toHaveBeenCalledWith(OVERVIEW.friends[0].maps[0]);
    expect(container.textContent).toBe('HOME');
  });

  it('공개된 지도가 없으면 안내한다', async () => {
    social.getFriendMaps.mockResolvedValue({ friends: [] });
    await render();
    expect(container.textContent).toContain('아직 공개된 지도가 없어요');
  });

  it('불러오지 못하면 이유와 다시 시도를 보여 준다', async () => {
    social.getFriendMaps.mockRejectedValueOnce(new Error('서버 오류'));
    social.getFriendMaps.mockResolvedValueOnce(OVERVIEW);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    await render();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('서버 오류');

    const retry = Array.from(container.querySelectorAll('button')).find((button) => button.textContent === '다시 시도') as HTMLButtonElement;
    await act(async () => {
      retry.click();
    });
    expect(container.textContent).toContain('맛집');
  });

  it('지도를 열지 못하면 이유를 알리고 이동하지 않는다', async () => {
    social.getFriendMaps.mockResolvedValue(OVERVIEW);
    useMapStore.setState({ openMap: vi.fn().mockRejectedValue(new Error('열 수 없어요')) });
    await render();

    const card = Array.from(container.querySelectorAll('button')).find((button) => button.textContent?.includes('맛집')) as HTMLButtonElement;
    await act(async () => {
      card.click();
    });

    expect(container.textContent).not.toBe('HOME');
    expect(container.querySelector('[role="alert"]')?.textContent).toBe('열 수 없어요');
  });

  it('로그인하지 않았으면 로그인 안내를 보여 준다', async () => {
    useAuthStore.setState({ user: null, isAuthenticated: false });
    await render();
    expect(social.getFriendMaps).not.toHaveBeenCalled();
    expect(container.textContent).toContain('로그인');
  });
});
