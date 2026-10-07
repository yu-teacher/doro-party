import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';
import { useAuthStore } from './store/authStore';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('#root 요소를 찾을 수 없습니다.');
}
const root = createRoot(rootElement);

// 서버에 로그인 상태를 먼저 확인한 뒤 화면을 그린다. (확인 전에 그리면 로그인 사용자가 잠깐 비로그인 화면을 보게 된다)
void useAuthStore.getState().loadSession().finally(() => {
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
