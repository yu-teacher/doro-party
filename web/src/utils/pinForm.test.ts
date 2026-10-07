import { describe, expect, it } from 'vitest';
import type { Pin } from '../api/types';
import { EMPTY_PIN_FORM, formValuesFromPin, toPinInput, validatePinForm } from './pinForm';

describe('validatePinForm', () => {
  it('이름이 비었거나 너무 길면 오류', () => {
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: '   ' }).name).toBeDefined();
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: '가'.repeat(101) }).name).toBeDefined();
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: '가'.repeat(100) })).toEqual({});
  });

  it('메모 길이와 평점 범위를 검사한다', () => {
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: 'a', sharedMemo: 'x'.repeat(2001) }).sharedMemo).toBeDefined();
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: 'a', rating: 6 }).rating).toBeDefined();
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: 'a', rating: 0 }).rating).toBeDefined();
    expect(validatePinForm({ ...EMPTY_PIN_FORM, name: 'a', rating: 5 })).toEqual({});
  });
});

describe('toPinInput', () => {
  it('공백을 정리하고 빈 메모는 null 로 보낸다', () => {
    const input = toPinInput({ ...EMPTY_PIN_FORM, name: '  연남 카페 ', sharedMemo: '  ', tags: ['카페'] }, 37.5, 127.0);
    expect(input).toEqual({ name: '연남 카페', sharedMemo: null, lat: 37.5, lng: 127.0, status: 'WISH', rating: null, tags: ['카페'] });
  });
});

describe('formValuesFromPin', () => {
  it('핀을 폼 값으로 되돌린다(메모가 없으면 빈 문자열)', () => {
    const pin: Pin = {
      id: 'p', mapId: 'm', createdBy: 'u', lat: 1, lng: 2, name: '이름', sharedMemo: null,
      status: 'VISITED', rating: 4, tags: ['a'], createdAt: '', updatedAt: '',
    };
    expect(formValuesFromPin(pin)).toEqual({ name: '이름', sharedMemo: '', status: 'VISITED', rating: 4, tags: ['a'] });
  });
});
