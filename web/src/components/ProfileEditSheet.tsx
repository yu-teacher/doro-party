import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import { useAuthStore } from '../store/authStore';
import Sheet from './Sheet';

interface Props {
  onClose: () => void;
}

/** 닉네임(친구에게 보이는 이름)과 사용자명(친구가 나를 찾는 이름)을 바꾼다. */
export default function ProfileEditSheet({ onClose }: Props) {
  const nicknameId = useId();
  const usernameId = useId();
  const user = useAuthStore((state) => state.user);
  const updateProfile = useAuthStore((state) => state.updateProfile);
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (nickname.trim() === '') {
      setError('닉네임을 입력해 주세요.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateProfile(nickname.trim(), username.trim());
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '저장하지 못했어요.');
      setSaving(false);
    }
  };

  return (
    <Sheet title="내 프로필" onClose={onClose}>
      <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4" noValidate>
        <div>
          <label htmlFor={nicknameId} className="mb-1.5 block text-sm font-medium text-slate-300">닉네임</label>
          <input id={nicknameId} value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={LIMITS.nickname} autoFocus
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none focus:border-teal-500" />
          <p className="mt-1 text-xs text-slate-500">친구와 모임에서 보이는 이름이에요.</p>
        </div>
        <div>
          <label htmlFor={usernameId} className="mb-1.5 block text-sm font-medium text-slate-300">사용자명</label>
          <div className="flex items-center rounded-lg border border-slate-700 bg-slate-950 focus-within:border-teal-500">
            <span className="pl-3 text-slate-500">@</span>
            <input id={usernameId} value={username} onChange={(event) => setUsername(event.target.value)} maxLength={LIMITS.username.max}
              autoCapitalize="none" autoCorrect="off" spellCheck={false}
              className="min-w-0 flex-1 bg-transparent px-2 py-2.5 text-slate-100 outline-none" />
          </div>
          <p className="mt-1 text-xs text-slate-500">친구가 나를 찾는 이름이에요. 영문 소문자, 숫자, _ 로 {LIMITS.username.min}~{LIMITS.username.max}자.</p>
        </div>
        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}
        <div className="flex gap-2">
          <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">취소</button>
          <button type="submit" disabled={saving} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
            {saving ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>
    </Sheet>
  );
}
