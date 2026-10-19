import * as pushApi from '../api/pushApi';
import type { PushSubscriptionJson } from '../api/pushApi';

/**
 * 이 기기(브라우저)의 푸시 구독을 다룬다.
 *  - supported: 켤 수 있다
 *  - needs-install: iPhone/iPad 는 홈 화면에 추가한 앱에서만 푸시를 받을 수 있다(사파리 탭에서는 불가)
 *  - unsupported: 이 브라우저는 푸시를 지원하지 않는다
 */
export type PushSupport = 'supported' | 'needs-install' | 'unsupported';

type NavigatorWithStandalone = Navigator & { standalone?: boolean };

function isIos(): boolean {
  const ua = navigator.userAgent;
  // iPadOS 는 데스크톱 사파리처럼 보이지만 터치가 된다
  return /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
}

function isInstalledApp(): boolean {
  return (navigator as NavigatorWithStandalone).standalone === true || window.matchMedia('(display-mode: standalone)').matches;
}

export function detectPushSupport(): PushSupport {
  if (isIos() && !isInstalledApp()) {
    return 'needs-install';
  }
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
    return 'unsupported';
  }
  return 'supported';
}

/** 서버가 알려 준 공개 키(base64url)를 브라우저가 쓰는 바이트로 바꾼다. */
export function urlBase64ToBytes(value: string): Uint8Array {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  const raw = atob(padded);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

function sameKey(subscription: PushSubscription, publicKey: string): boolean {
  const current = subscription.options.applicationServerKey;
  if (current === null || current === undefined) {
    return false;
  }
  const expected = urlBase64ToBytes(publicKey);
  const actual = new Uint8Array(current);
  return actual.length === expected.length && actual.every((byte, index) => byte === expected[index]);
}

function toSubscriptionJson(subscription: PushSubscription): PushSubscriptionJson {
  const json = subscription.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) {
    throw new Error('이 기기의 푸시 구독 정보를 읽지 못했어요.');
  }
  return { endpoint: json.endpoint, keys: { p256dh: json.keys.p256dh, auth: json.keys.auth } };
}

async function currentSubscription(): Promise<PushSubscription | null> {
  const registration = await navigator.serviceWorker.ready;
  return registration.pushManager.getSubscription();
}

/** 이 기기의 알림 권한: 'granted' | 'denied' | 'default' */
export function notificationPermission(): NotificationPermission {
  return Notification.permission;
}

/** 이 기기에 구독이 있는지(권한이 허용된 경우에만 의미가 있다). */
export async function hasLocalSubscription(): Promise<boolean> {
  return (await currentSubscription()) !== null;
}

/**
 * 이 기기로 알림을 받도록 켠다: 권한을 묻고(허용해야 한다), 브라우저에 구독을 만들고, 서버에 등록한다.
 * 사용자가 누른 직후에 불러야 한다(브라우저는 사용자 동작 없이 권한을 묻지 못하게 한다).
 * @throws 권한을 거부했거나 구독/등록에 실패하면 안내 문구가 담긴 오류
 */
export async function enableThisDevice(publicKey: string): Promise<void> {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    throw new Error('알림 권한이 허용되지 않았어요. 브라우저(또는 기기) 설정에서 알림을 허용해 주세요.');
  }
  const registration = await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  if (subscription !== null && !sameKey(subscription, publicKey)) {
    // 서버의 푸시 키가 바뀌었다: 옛 구독으로는 더는 보낼 수 없으니 새로 만든다
    await subscription.unsubscribe();
    subscription = null;
  }
  const active = subscription ?? (await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToBytes(publicKey) }));
  await pushApi.saveSubscription(toSubscriptionJson(active));
}

/** 이 기기의 알림을 끈다: 서버에서 지우고 브라우저 구독도 해제한다. */
export async function disableThisDevice(): Promise<void> {
  const subscription = await currentSubscription();
  if (subscription === null) {
    return;
  }
  await pushApi.deleteSubscription(subscription.endpoint);
  await subscription.unsubscribe();
}

/**
 * 로그아웃 전에 이 기기를 내 계정에서 떼어 낸다(서버에서만 지운다). 브라우저 구독은 남겨서 다시 로그인하면 이어서 쓴다.
 * 안 떼면 같은 기기로 다른 사람이 로그인해도 이전 계정의 알림이 이 기기로 온다.
 */
export async function detachThisDevice(): Promise<void> {
  if (detectPushSupport() !== 'supported' || notificationPermission() !== 'granted') {
    return;
  }
  const subscription = await currentSubscription();
  if (subscription !== null) {
    await pushApi.deleteSubscription(subscription.endpoint);
  }
}

/**
 * 로그인한 뒤 이 기기의 구독을 서버와 맞춘다(구독 주소가 바뀌었거나, 로그아웃으로 떼어 냈거나, 서버 키가 바뀐 경우).
 * 권한이 이미 허용돼 있고 이 기기에 구독이 있을 때만 하며, 권한을 새로 묻지는 않는다.
 * @return 이 기기가 알림을 받는 상태인지
 */
export async function syncThisDevice(publicKey: string): Promise<boolean> {
  if (notificationPermission() !== 'granted') {
    return false;
  }
  const subscription = await currentSubscription();
  if (subscription === null) {
    return false;
  }
  if (!sameKey(subscription, publicKey)) {
    await enableThisDevice(publicKey);
    return true;
  }
  await pushApi.saveSubscription(toSubscriptionJson(subscription));
  return true;
}

/** 알림에 담긴 주소(서비스 워커가 보낸 절대 주소)를 앱 안의 이동 경로로 바꾼다. 앱 범위 밖이면 null. */
export function toRouterPath(href: string, origin: string, basename: string): string | null {
  let url: URL;
  try {
    url = new URL(href, origin);
  } catch {
    return null;
  }
  const base = basename.replace(/\/+$/, '');
  const inScope = url.pathname === base || url.pathname.startsWith(`${base}/`);
  if (url.origin !== origin || !inScope) {
    return null;
  }
  const path = url.pathname.slice(base.length) || '/';
  return `${path}${url.search}`;
}
