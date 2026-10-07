import { Map as MapIcon, PartyPopper, Users } from 'lucide-react';
import { NavLink } from 'react-router-dom';

const TABS = [
  { to: '/', label: '내 지도', icon: MapIcon, end: true },
  { to: '/friends', label: '친구', icon: Users, end: false },
  { to: '/groups', label: '모임', icon: PartyPopper, end: false },
] as const;

export default function TabBar() {
  return (
    <nav className="grid grid-cols-3 border-t border-slate-800 bg-slate-900 pb-[env(safe-area-inset-bottom)]" aria-label="주요 메뉴">
      {TABS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            `flex flex-col items-center gap-1 py-2.5 text-xs font-medium ${isActive ? 'text-teal-400' : 'text-slate-400 hover:text-slate-200'}`
          }
        >
          <Icon size={20} aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
