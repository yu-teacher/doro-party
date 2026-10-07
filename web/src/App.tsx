import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ROUTER_BASENAME } from './config';
import Header from './components/Header';
import LoginErrorNotice from './components/LoginErrorNotice';
import TabBar from './components/TabBar';
import ComingSoonPage from './pages/ComingSoonPage';
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
            <Route path="/friends" element={<ComingSoonPage title="친구" description="친구를 추가하고 서로의 지도를 공유하는 화면이에요." />} />
            <Route path="/groups" element={<ComingSoonPage title="모임" description="모임을 만들어 친구들의 지도를 한 화면에 겹쳐 보는 화면이에요." />} />
            <Route path="*" element={<ComingSoonPage title="없는 페이지예요" description="주소를 확인하거나 아래 메뉴로 이동해 주세요." />} />
          </Routes>
        </main>
        <TabBar />
      </div>
    </BrowserRouter>
  );
}
