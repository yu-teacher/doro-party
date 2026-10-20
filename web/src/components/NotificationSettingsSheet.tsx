import { useEffect, useId, useState } from 'react';
import type { PushPreferences } from '../api/pushApi';
import { usePushStore } from '../store/pushStore';
import type { PushStatus } from '../store/pushStore';
import Sheet from './Sheet';

interface Props {
  onClose: () => void;
}

const PREFERENCE_LABELS: ReadonlyArray<{ key: keyof PushPreferences; label: string; hint: string }> = [
  { key: 'comments', label: '댓글·답글', hint: '내가 꽂았거나 댓글을 남긴 핀에 새 댓글이 달렸을 때, 내 댓글에 답글이 달렸을 때' },
  { key: 'friendRequests', label: '친구', hint: '친구 요청을 받았거나 내 요청이 수락됐을 때' },
  { key: 'shares', label: '지도 공유', hint: '친구가 내게 지도를 공유했을 때' },
];

/** 상태별로 사용자에게 보여 줄 설명. 켤 수 없는 경우는 이유와 해결 방법을 알려 준다. */
const STATUS_TEXT: Record<Exclude<PushStatus, 'on' | 'off' | 'loading'>, string> = {
  unsupported: '이 브라우저는 푸시 알림을 지원하지 않아요. 최신 크롬·사파리·엣지에서 열어 주세요.',
  'needs-install': '아이폰·아이패드에서는 사파리의 공유 버튼 → "홈 화면에 추가"로 앱을 설치한 뒤, 설치한 앱에서 알림을 켤 수 있어요.',
  'server-disabled': '이 서버에서는 아직 푸시 알림을 쓸 수 없어요.',
  denied: '이 기기에서 알림이 막혀 있어요. 브라우저(또는 기기) 설정에서 도로 파티의 알림을 허용한 뒤 다시 열어 주세요.',
};

/** 이 기기로 알림을 받을지, 어떤 알림을 받을지 정한다. */
function NotificationSettingsSheet({ onClose }: Props) {
  const { status, preferences, busy, error } = usePushStore();
  const { refresh, enable, disable, setPreference, sendTest } = usePushStore.getState();
  const [testResult, setTestResult] = useState<string | null>(null);
  const groupId = useId();

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <Sheet title="알림" onClose={onClose}>
      <div className="flex flex-col gap-4">
        {status === 'loading' && <p className="text-sm text-slate-400">알림 상태를 확인하는 중…</p>}

        {status !== 'loading' && status !== 'on' && status !== 'off' && (
          <p role="status" className="rounded-lg bg-slate-800/70 px-3 py-2.5 text-sm leading-relaxed text-slate-300">{STATUS_TEXT[status]}</p>
        )}

        {status === 'off' && (
          <>
            <p className="text-sm leading-relaxed text-slate-300">앱을 닫아 둬도 새 댓글, 친구 요청, 지도 공유를 이 기기로 알려 드려요.</p>
            <button type="button" onClick={() => void enable()} disabled={busy}
              className="rounded-lg bg-teal-500 px-4 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
              {busy ? '켜는 중…' : '이 기기에서 알림 받기'}
            </button>
          </>
        )}

        {status === 'on' && (
          <>
            <p role="status" className="rounded-lg bg-teal-500/15 px-3 py-2.5 text-sm text-teal-100">이 기기에서 알림을 받고 있어요.</p>
            {preferences !== null && (
              <fieldset className="flex flex-col gap-1" aria-labelledby={groupId}>
                <legend id={groupId} className="mb-1 text-sm font-semibold text-slate-200">받을 알림</legend>
                {PREFERENCE_LABELS.map(({ key, label, hint }) => (
                  <label key={key} className="flex cursor-pointer items-start gap-3 rounded-lg px-1 py-2 hover:bg-slate-800/50">
                    <input type="checkbox" checked={preferences[key]} onChange={(event) => void setPreference(key, event.target.checked)}
                      className="mt-0.5 h-4 w-4 shrink-0 accent-teal-500" />
                    <span className="flex flex-col">
                      <span className="text-sm text-slate-100">{label}</span>
                      <span className="text-xs text-slate-500">{hint}</span>
                    </span>
                  </label>
                ))}
              </fieldset>
            )}
            <div className="flex gap-2">
              <button type="button" disabled={busy} onClick={() => void sendTest().then(setTestResult)}
                className="flex-1 rounded-lg bg-slate-800 px-3 py-2.5 text-sm font-medium text-slate-100 hover:bg-slate-700 disabled:opacity-60">
                시험 알림 보내기
              </button>
              <button type="button" disabled={busy} onClick={() => void disable()}
                className="flex-1 rounded-lg px-3 py-2.5 text-sm font-medium text-rose-300 ring-1 ring-slate-700 hover:bg-slate-800 disabled:opacity-60">
                이 기기 알림 끄기
              </button>
            </div>
            {testResult !== null && <p role="status" className="text-xs text-slate-400">{testResult}</p>}
          </>
        )}

        {error !== null && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{error}</p>}
      </div>
    </Sheet>
  );
}

/** 화면 영역 안에서 알림 설정 시트를 그린다(열림 상태는 pushStore). */
export default function NotificationSettingsHost() {
  const open = usePushStore((state) => state.settingsOpen);
  const close = usePushStore((state) => state.closeSettings);
  return open ? <NotificationSettingsSheet onClose={close} /> : null;
}
