import { LIMITS } from '../api/types';
import type { Pin, PinInput, PinStatus, RevisitIntent } from '../api/types';

export interface PinFormValues {
  name: string;
  sharedMemo: string;
  status: PinStatus;
  rating: number | null;
  revisitIntent: RevisitIntent | null;
  tags: string[];
}

export type PinFormErrors = Partial<Record<'name' | 'sharedMemo' | 'rating', string>>;

export const EMPTY_PIN_FORM: PinFormValues = { name: '', sharedMemo: '', status: 'WISH', rating: null, revisitIntent: null, tags: [] };

export function formValuesFromPin(pin: Pin): PinFormValues {
  return { name: pin.name, sharedMemo: pin.sharedMemo ?? '', status: pin.status, rating: pin.rating, revisitIntent: pin.revisitIntent, tags: pin.tags };
}

/** 서버가 거부할 입력을 미리 알려 준다. 빈 객체면 보낼 수 있다. */
export function validatePinForm(values: PinFormValues): PinFormErrors {
  const errors: PinFormErrors = {};
  const name = values.name.trim();
  if (name.length === 0) {
    errors.name = '이름을 입력해 주세요.';
  } else if (name.length > LIMITS.pinName) {
    errors.name = `이름은 ${LIMITS.pinName}자까지 입력할 수 있어요.`;
  }
  if (values.sharedMemo.length > LIMITS.pinMemo) {
    errors.sharedMemo = `메모는 ${LIMITS.pinMemo}자까지 입력할 수 있어요.`;
  }
  if (values.rating !== null && (values.rating < LIMITS.ratingMin || values.rating > LIMITS.ratingMax)) {
    errors.rating = '평점은 1~5점이에요.';
  }
  return errors;
}

export function toPinInput(values: PinFormValues, lat: number, lng: number): PinInput {
  const memo = values.sharedMemo.trim();
  return {
    name: values.name.trim(),
    sharedMemo: memo.length > 0 ? memo : null,
    lat,
    lng,
    status: values.status,
    rating: values.rating,
    // 재방문 의사는 다녀온 곳에만 의미가 있다
    revisitIntent: values.status === 'VISITED' ? values.revisitIntent : null,
    tags: values.tags,
  };
}
