import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ROUTER_BASENAME } from './config';
import Header from './components/Header';
import LoginErrorNotice from './components/LoginErrorNotice';
import TabBar from './components/TabBar';
import ComingSoonPage from './pages/ComingSoonPage';
import FriendMapsPage from './pages/FriendMapsPage';
import FriendsPage from './pages/FriendsPage';
import GroupPage from './pages/GroupPage';
import GroupsPage from './pages/GroupsPage';
import InvitePage from './pages/InvitePage';
import JoinGroupPage from './pages/JoinGroupPage';
import MapPage from './pages/MapPage';

export default function App() {
  return (
    <BrowserRouter basename={ROUTER_BASENAME}>
      <div className="flex h-dvh flex-col">
        <Header />
        <LoginErrorNotice />
        <main className="min-h-0 flex-1">
          <Routes>
            <Route path="/" element={<MapPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/friends/maps" element={<FriendMapsPage />} />
            <Route path="/invite/:code" element={<InvitePage />} />
            <Route path="/groups" element={<GroupsPage />} />
            <Route path="/groups/:groupId" element={<GroupPage />} />
            <Route path="/join/:code" element={<JoinGroupPage />} />
            <Route path="*" element={<ComingSoonPage title="없는 페이지예요" description="주소를 확인하거나 아래 메뉴로 이동해 주세요." />} />
          </Routes>
        </main>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
