import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as recordsApi from '../api/recordsApi';
import type { Photo, Pin } from '../api/types';
import { useMapStore } from '../store/mapStore';
import { prepareImage } from '../utils/image';
import PinSheet from './PinSheet';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
vi.mock('../api/recordsApi');
vi.mock('../utils/image');
const api = vi.mocked(recordsApi);
const prepare = vi.mocked(prepareImage);

const PIN: Pin = {
  id: 'p1', mapId: 'm1', createdBy: 'u', authorNickname: 'n', authorColor: '#10B981', lat: 37.5, lng: 127, name: '국밥집', sharedMemo: null,
  status: 'WISH', rating: null, revisitIntent: null, tags: [], visitCount: 0, lastVisitedOn: null, photoCount: 0, commentCount: 0, createdAt: '', updatedAt: '',
};
const photo = (id: string): Photo => ({ id, pinId: 'p1', uploadedBy: 'u', contentType: 'image/jpeg', sizeBytes: 1, createdAt: '' });
const file = (name: string) => new File([name], name, { type: 'image/jpeg' });

let container: HTMLDivElement;
let root: Root;
const addPin = vi.fn();
const reloadPins = vi.fn();
const onCreated = vi.fn();
const onClose = vi.fn();

beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => undefined }));
  addPin.mockResolvedValue(PIN);
  reloadPins.mockResolvedValue(undefined);
  useMapStore.setState({ addPin, reloadPins });
  prepare.mockImplementation(async (f) => new Blob([f.name]));
  let n = 0;
  api.uploadPhoto.mockImplementation(async () => photo(`ph${++n}`));
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

const render = () => act(async () => root.render(<PinSheet mode="create" lat={37.5} lng={127} onCreated={onCreated} onClose={onClose} />));
const button = (text: string) => [...container.querySelectorAll('button')].find((b) => b.textContent?.includes(text));

async function typeName(value: string) {
  const name = container.querySelector<HTMLInputElement>('input[placeholder]') as HTMLInputElement;
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set?.call(name, value);
    name.dispatchEvent(new Event('input', { bubbles: true }));
  });
}
async function pick(...files: File[]) {
  const input = container.querySelector<HTMLInputElement>('input[type="file"]') as HTMLInputElement;
  Object.defineProperty(input, 'files', { configurable: true, value: files });
  await act(async () => { input.dispatchEvent(new Event('change', { bubbles: true })); });
}
const save = () => act(async () => button('핀 꽂기')?.click());

describe('새 핀에 사진 붙이기', () => {
  it('새 핀 화면에서 바로 사진을 고를 수 있다', async () => {
    await render();
    expect(container.querySelector('input[type="file"]')).not.toBeNull();
    expect(container.textContent).toContain('사진 추가');
  });

  it('사진 없이 저장하면 예전처럼 핀만 저장하고 바로 연다(사진 업로드를 부르지 않는다)', async () => {
    await render();
    await typeName('국밥집');
    await save();
    expect(addPin).toHaveBeenCalledTimes(1);
    expect(api.uploadPhoto).not.toHaveBeenCalled();
    expect(onCreated).toHaveBeenCalledWith(PIN);
  });

  it('사진을 골라 저장하면 핀을 먼저 저장한 뒤 고른 순서대로 올리고, 핀 목록을 새로 불러온 다음 연다', async () => {
    const order: string[] = [];
    addPin.mockImplementation(async () => { order.push('addPin'); return PIN; });
    api.uploadPhoto.mockImplementation(async (_m, _p, blob) => { order.push(`upload ${await (blob as Blob).text()}`); return photo('x'); });
    reloadPins.mockImplementation(async () => { order.push('reloadPins'); });
    onCreated.mockImplementation(() => order.push('onCreated'));
    await render();
    await typeName('국밥집');
    await pick(file('a.jpg'), file('b.jpg'));

    await save();

    expect(order).toEqual(['addPin', 'upload a.jpg', 'upload b.jpg', 'reloadPins', 'onCreated']);
    expect(api.uploadPhoto).toHaveBeenNthCalledWith(1, 'm1', 'p1', expect.any(Blob));
  });

  it('올리는 동안 버튼에 진행 상황이 보인다', async () => {
    let finish: (value: Photo) => void = () => undefined;
    api.uploadPhoto.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    await render();
    await typeName('국밥집');
    await pick(file('a.jpg'), file('b.jpg'));

    await save();
    expect(container.textContent).toContain('사진 올리는 중 (1/2)');

    await act(async () => finish(photo('p')));
    expect(onCreated).toHaveBeenCalled();
  });

  it('일부 사진이 실패해도 핀은 저장돼 있다: 결과를 알려 주고, 확인하면 핀을 연다', async () => {
    api.uploadPhoto.mockResolvedValueOnce(photo('ok')).mockRejectedValueOnce(new Error('서버 오류'));
    await render();
    await typeName('국밥집');
    await pick(file('a.jpg'), file('b.jpg'));

    await save();

    expect(onCreated).not.toHaveBeenCalled();
    expect(container.textContent).toContain('핀은 저장했어요');
    expect(container.textContent).toContain('사진 1건을 올리지 못했어요');
    expect(container.textContent).toContain('b.jpg: 서버 오류');
    await act(async () => button('핀 보기')?.click());
    expect(onCreated).toHaveBeenCalledWith(PIN);
    expect(addPin).toHaveBeenCalledTimes(1);   // 다시 저장해 같은 핀이 또 만들어지지 않는다
  });

  it('핀 저장 자체가 실패하면 사진은 올리지 않고, 오류를 보여 주며, 고른 사진은 그대로라 다시 시도할 수 있다', async () => {
    addPin.mockRejectedValueOnce(new Error('핀은 지도마다 최대 2000개까지 꽂을 수 있습니다.'));
    await render();
    await typeName('국밥집');
    await pick(file('a.jpg'));

    await save();

    expect(api.uploadPhoto).not.toHaveBeenCalled();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('최대 2000개');
    expect(container.querySelectorAll('img')).toHaveLength(1);

    await save();   // 다시 시도
    expect(addPin).toHaveBeenCalledTimes(2);
    expect(api.uploadPhoto).toHaveBeenCalledTimes(1);
  });

  it('사진을 올린 뒤 핀 목록을 새로 불러오지 못해도 핀을 다시 만들지 않고 정상적으로 연다', async () => {
    reloadPins.mockRejectedValue(new Error('네트워크 오류'));
    await render();
    await typeName('국밥집');
    await pick(file('a.jpg'));

    await save();

    expect(onCreated).toHaveBeenCalledWith(PIN);
    expect(addPin).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('이름이 비어 있으면 저장하지 않고 사진 업로드도 하지 않는다', async () => {
    await render();
    await pick(file('a.jpg'));
    await save();
    expect(addPin).not.toHaveBeenCalled();
    expect(api.uploadPhoto).not.toHaveBeenCalled();
  });
});
