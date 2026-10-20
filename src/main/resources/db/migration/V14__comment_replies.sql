-- DORO Party V14: 대댓글(한 단계)과 삭제된 댓글 표시
--  - parent_id: 답글이 속한 맨 위 댓글. 맨 위 댓글은 NULL (답글의 답글도 같은 맨 위 댓글에 붙는다)
--  - reply_to_user_id: 답글이 향한 사람(누구에게 답한 글인지 표시하고 알림을 보낸다)
--  - deleted_at: 답글이 달린 댓글을 지우면 행은 남기고(내용은 비운다) "삭제된 댓글" 로 보여 준다. 답글이 없으면 그냥 지운다.
ALTER TABLE pin_comments
    ADD COLUMN parent_id UUID REFERENCES pin_comments(id) ON DELETE CASCADE,
    ADD COLUMN reply_to_user_id UUID REFERENCES party_users(id) ON DELETE SET NULL,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_pin_comments_parent ON pin_comments(parent_id) WHERE parent_id IS NOT NULL;
