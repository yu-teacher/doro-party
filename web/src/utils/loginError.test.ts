import { describe, expect, it } from 'vitest';
import { readLoginError, withoutLoginError } from './loginError';

describe('readLoginError', () => {
  it('알려진 값만 돌려준다', () => {
    expect(readLoginError('?login_error=failed')).toBe('failed');
    expect(readLoginError('?login_error=cancelled')).toBe('cancelled');
    expect(readLoginError('?login_error=<script>')).toBeNull();
    expect(readLoginError('')).toBeNull();
  });
});

describe('withoutLoginError', () => {
  it('다른 쿼리는 남기고 login_error 만 지운다', () => {
    expect(withoutLoginError('?login_error=failed&tab=a')).toBe('?tab=a');
    expect(withoutLoginError('?login_error=failed')).toBe('');
  });
});
