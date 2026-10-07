export interface UserProfile {
  id: string;
  username: string;
  nickname: string;
  /** 겹쳐보기에서 이 사람의 핀을 구분하는 색(#RRGGBB) */
  color: string;
}
