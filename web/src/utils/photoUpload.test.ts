import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as recordsApi from '../api/recordsApi';
import type { Photo } from '../api/types';
import { prepareImage } from './image';
import { uploadPhotoFiles } from './photoUpload';

vi.mock('../api/recordsApi');
vi.mock('./image');
const api = vi.mocked(recordsApi);
const prepare = vi.mocked(prepareImage);

const file = (name: string) => new File(['x'], name, { type: 'image/jpeg' });
const photo = (id: string): Photo => ({ id, pinId: 'p', uploadedBy: 'u', contentType: 'image/jpeg', sizeBytes: 1, createdAt: '' });

beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  prepare.mockImplementation(async (f) => new Blob([f.name]));
  let n = 0;
  api.uploadPhoto.mockImplementation(async () => photo(`ph${++n}`));
});

describe('사진 올리기', () => {
  it('한 장씩 줄여서 올리고, 올라갈 때마다 알린다', async () => {
    const uploaded: string[] = [];
    const progress: Array<[number, number]> = [];

    const failures = await uploadPhotoFiles('m', 'p', [file('a.jpg'), file('b.jpg')], {
      room: 10, onUploaded: (p) => uploaded.push(p.id), onProgress: (done, total) => progress.push([done, total]),
    });

    expect(failures).toEqual([]);
    expect(uploaded).toEqual(['ph1', 'ph2']);
    expect(progress).toEqual([[1, 2], [2, 2]]);
    expect(api.uploadPhoto).toHaveBeenCalledTimes(2);
    expect(api.uploadPhoto).toHaveBeenNthCalledWith(1, 'm', 'p', expect.any(Blob));
  });

  it('올리는 순서는 고른 순서 그대로다(앞 사진이 끝나야 다음을 올린다)', async () => {
    const order: string[] = [];
    prepare.mockImplementation(async (f) => { order.push(`prepare ${f.name}`); return new Blob([f.name]); });
    api.uploadPhoto.mockImplementation(async (_m, _p, blob) => { order.push(`upload ${await (blob as Blob).text()}`); return photo('x'); });

    await uploadPhotoFiles('m', 'p', [file('a.jpg'), file('b.jpg')], { room: 10 });

    expect(order).toEqual(['prepare a.jpg', 'upload a.jpg', 'prepare b.jpg', 'upload b.jpg']);
  });

  it('한 장이 실패해도 나머지는 계속 올리고 사유를 돌려준다(던지지 않는다)', async () => {
    api.uploadPhoto.mockResolvedValueOnce(photo('ok1')).mockRejectedValueOnce(new Error('서버 오류')).mockResolvedValueOnce(photo('ok3'));
    const uploaded: string[] = [];

    const failures = await uploadPhotoFiles('m', 'p', [file('a.jpg'), file('b.jpg'), file('c.jpg')], { room: 10, onUploaded: (p) => uploaded.push(p.id) });

    expect(uploaded).toEqual(['ok1', 'ok3']);
    expect(failures).toEqual(['b.jpg: 서버 오류']);
  });

  it('읽을 수 없는 사진(줄이기 실패)도 그 사진만 실패로 센다', async () => {
    prepare.mockRejectedValueOnce(new Error('이 사진을 읽을 수 없어요. 다른 사진으로 시도해 주세요.'));
    const failures = await uploadPhotoFiles('m', 'p', [file('bad.heic'), file('ok.jpg')], { room: 10 });
    expect(failures).toEqual(['bad.heic: 이 사진을 읽을 수 없어요. 다른 사진으로 시도해 주세요.']);
    expect(api.uploadPhoto).toHaveBeenCalledTimes(1);
  });

  it('핀마다 상한을 넘는 사진은 올리지 않고 안내한다', async () => {
    const failures = await uploadPhotoFiles('m', 'p', [file('a.jpg'), file('b.jpg'), file('c.jpg')], { room: 2 });
    expect(api.uploadPhoto).toHaveBeenCalledTimes(2);
    expect(failures).toHaveLength(1);
    expect(failures[0]).toContain('2장만 올려요');
  });

  it('남은 자리가 없으면(0 이하) 아무것도 올리지 않는다', async () => {
    const failures = await uploadPhotoFiles('m', 'p', [file('a.jpg')], { room: -3 });
    expect(api.uploadPhoto).not.toHaveBeenCalled();
    expect(failures[0]).toContain('0장만 올려요');
  });

  it('사진이 없으면 아무 일도 하지 않는다', async () => {
    expect(await uploadPhotoFiles('m', 'p', [], { room: 10 })).toEqual([]);
    expect(api.uploadPhoto).not.toHaveBeenCalled();
  });
});
