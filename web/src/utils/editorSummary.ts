import type { FriendAccess, ShareView } from '../api/types';

export interface EditorSummary {
  /** 직접 "핀도 꽂기"를 받은 친구들의 닉네임(이름순) */
  names: string[];
  /** 친구 전체에게 편집으로 공개했는지 */
  everyFriend: boolean;
}

/** 이 지도에 핀을 추가할 수 있는 사람(주인 제외): 직접 편집 권한을 준 친구와, 친구 전체 편집 공개. 모임 공유는 보기만이라 포함되지 않는다. */
export function summarizeEditors(shares: ReadonlyArray<Pick<ShareView, 'role' | 'user'>>, friendAccess: FriendAccess): EditorSummary {
  const names = shares
    .filter((share) => share.role === 'EDITOR')
    .map((share) => share.user.nickname)
    .sort((a, b) => a.localeCompare(b, 'ko'));
  return { names, everyFriend: friendAccess === 'EDITOR' };
}

/** 주인 말고도 핀을 추가할 수 있는 사람이 있는지 */
export function hasOtherEditors(summary: EditorSummary): boolean {
  return summary.everyFriend || summary.names.length > 0;
}

/** 화면에 늘어놓을 이름들: 나(주인), 직접 편집 권한을 받은 친구들, 친구 전체 편집이면 "내 친구 전체". */
export function editorLabels(summary: EditorSummary): string[] {
  return ['나', ...summary.names, ...(summary.everyFriend ? ['내 친구 전체'] : [])];
}
