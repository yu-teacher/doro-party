import { ChevronRight, Plus } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as socialApi from '../api/socialApi';
import GroupNameSheet from '../components/GroupNameSheet';
import LoginRequired from '../components/LoginRequired';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';

export default function GroupsPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="모임" description="로그인하면 친구들과 모임을 만들고, 내 지도를 모임에 공유할 수 있어요." />;
  }
  return <GroupsContent />;
}

function GroupsContent() {
  const navigate = useNavigate();
  const groups = useResource(useCallback((signal: AbortSignal) => socialApi.listGroups(signal), []));
  const [creating, setCreating] = useState(false);
  const list = groups.data ?? [];

  return (
    <div className="relative h-full">
      <div className="flex h-full flex-col gap-4 overflow-y-auto p-4 pb-8">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-slate-100">내 모임</h2>
          <button type="button" onClick={() => setCreating(true)} className="flex items-center gap-1.5 rounded-lg bg-teal-500 px-3.5 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-400">
            <Plus size={15} /> 모임 만들기
          </button>
        </div>

        {groups.error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{groups.error}</p>}

        {list.length === 0 && !groups.loading ? (
          <div className="rounded-xl bg-slate-800/50 p-5 text-center text-sm leading-relaxed text-slate-400">
            아직 모임이 없어요.<br />카톡방처럼 모임을 만들고, 링크로 친구들을 초대한 뒤<br />서로의 지도를 한 곳에서 같이 봐요.
          </div>
        ) : (
          <ul className="flex flex-col gap-2">
            {list.map((group) => (
              <li key={group.id}>
                <Link to={`/groups/${group.id}`} className="flex items-center justify-between gap-3 rounded-xl bg-slate-800/60 p-4 hover:bg-slate-800">
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 truncate font-semibold text-slate-100">
                      {group.name}
                      {group.myRole === 'OWNER' && <span className="shrink-0 rounded-full bg-amber-400/15 px-2 py-0.5 text-[10px] font-semibold text-amber-200">방장</span>}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">멤버 {group.memberCount}명 · 공유된 지도 {group.mapCount}개</p>
                  </div>
                  <ChevronRight size={18} className="shrink-0 text-slate-500" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {creating && (
        <GroupNameSheet
          title="새 모임"
          initialName=""
          submitLabel="만들기"
          onSubmit={async (name) => {
            const created = await socialApi.createGroup(name);
            navigate(`/groups/${created.id}`);
          }}
          onClose={() => setCreating(false)}
        />
      )}
    </div>
  );
}
