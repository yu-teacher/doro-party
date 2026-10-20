/*
 * 도로 파티 푸시 알림 처리. workbox 가 만드는 서비스 워커(sw.js)가 importScripts 로 불러온다(vite.config.ts 의 workbox.importScripts).
 *  - push: 서버가 보낸 JSON({title, body, url, tag})으로 알림을 띄운다. 같은 tag 의 알림은 서로 바꿔치기된다.
 *  - notificationclick: 앱이 열려 있으면 그 창을 앞으로 가져와 앱 안에서 해당 화면으로 이동시키고(새로고침 없이), 없으면 새 창으로 연다.
 */
const FALLBACK = { title: '도로 파티', body: '새 알림이 있어요', url: './', tag: 'party' };
const MESSAGE_PUSH_RECEIVED = 'party-push-received';
const MESSAGE_OPEN = 'party-open';

function text(value) {
  return typeof value === 'string' ? value : '';
}

function readMessage(event) {
  if (!event.data) {
    return FALLBACK;
  }
  try {
    const data = event.data.json();
    return {
      title: text(data.title) || FALLBACK.title,
      body: text(data.body),
      url: text(data.url) || FALLBACK.url,
      tag: text(data.tag) || FALLBACK.tag,
    };
  } catch (error) {
    // 형식이 다른 메시지여도 알림은 띄운다(푸시를 받고도 알림을 안 띄우면 브라우저가 구독을 제한할 수 있다)
    console.warn('Push payload is not valid JSON', error);
    return FALLBACK;
  }
}

/** 열려 있는 앱 창에 새 알림이 왔음을 알린다(새 댓글 표시 등을 바로 갱신하게). */
function notifyOpenWindows() {
  return self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((windows) => windows.forEach((client) => client.postMessage({ type: MESSAGE_PUSH_RECEIVED })));
}

/** 알림에 담긴 주소는 이 앱의 범위 안(같은 출처, /party/ 아래)일 때만 연다. 그 밖이면 앱 첫 화면으로 보낸다. */
function resolveTarget(raw) {
  const scope = new URL(self.registration.scope);
  // 주소가 없을 때 new URL(undefined) 는 "undefined" 라는 경로가 되므로 먼저 걸러 낸다
  if (typeof raw !== 'string' || raw === '') {
    return scope.href;
  }
  try {
    const target = new URL(raw, scope);
    if (target.origin === scope.origin && target.pathname.startsWith(scope.pathname)) {
      return target.href;
    }
  } catch (error) {
    console.warn('Invalid notification url', error);
  }
  return scope.href;
}

self.addEventListener('push', (event) => {
  const message = readMessage(event);
  event.waitUntil(
    Promise.all([
      self.registration.showNotification(message.title, {
        body: message.body,
        tag: message.tag,
        renotify: true,
        icon: new URL('icon-192.png', self.registration.scope).href,
        data: { url: message.url },
      }),
      notifyOpenWindows(),
    ]),
  );
});

/** 앱 창이 이동 메시지를 받았다고 답하길 기다리는 시간(ms). 이 안에 답이 없으면(옛 버전 화면 등) 주소로 직접 이동시킨다. */
const OPEN_ACK_TIMEOUT_MS = 800;

/** 이 앱의 창이 여럿이면 지금 보고 있는 창(포커스 > 보이는 창 > 첫 창)을 고른다. */
function pickAppWindow(windows) {
  const scope = self.registration.scope;
  const mine = windows.filter((client) => client.url.startsWith(scope));
  return mine.find((client) => client.focused) || mine.find((client) => client.visibilityState === 'visible') || mine[0];
}

/** 앱 창에 이동 메시지를 보내고, 창이 처리했다고 답하면 true(답이 없으면 시간이 지난 뒤 false). */
function askToOpen(client, target) {
  return new Promise((resolve) => {
    const channel = new MessageChannel();
    const timer = setTimeout(() => resolve(false), OPEN_ACK_TIMEOUT_MS);
    channel.port1.onmessage = () => {
      clearTimeout(timer);
      channel.port1.close();
      resolve(true);
    };
    client.postMessage({ type: MESSAGE_OPEN, url: target }, [channel.port2]);
  });
}

/**
 * 알림을 누르면 해당 화면으로 반드시 가게 한다.
 *  1) 열려 있는 앱 창이 있으면 앞으로 가져오고(실패해도 계속), 앱 안에서 이동하라고 알린다(새로고침 없이).
 *  2) 창이 처리했다고 답하지 않으면(옛 버전 화면, 메시지를 못 받는 상태) 그 창을 주소로 직접 이동시킨다.
 *  3) 그것도 안 되거나 열린 창이 없으면 새 창으로 연다.
 */
async function openTarget(target) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const open = pickAppWindow(windows);
  if (!open) {
    return self.clients.openWindow(target);
  }
  try {
    await open.focus();
  } catch (error) {
    console.warn('Could not focus the app window', error);
  }
  if (await askToOpen(open, target)) {
    return undefined;
  }
  try {
    if (await open.navigate(target)) {
      return undefined;
    }
  } catch (error) {
    console.warn('Could not navigate the app window', error);
  }
  return self.clients.openWindow(target);
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(openTarget(resolveTarget(event.notification.data && event.notification.data.url)));
});
