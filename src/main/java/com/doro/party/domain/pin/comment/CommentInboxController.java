package com.doro.party.domain.pin.comment;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.comment.PinCommentDtos.UnreadPin;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "3. Comments (핀 댓글)")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentInboxController {

    private final PinCommentService commentService;

    @Operation(summary = "새 댓글이 달린 내 핀 (로그인)", description = "내가 꽂았거나 내가 댓글을 남긴 핀에서 읽지 않은 댓글. 볼 수 없게 된 지도의 핀은 빠진다")
    @GetMapping("/unread")
    public ApiResponse<List<UnreadPin>> unread(@CurrentDoroUser DoroUser user) {
        if (user == null || !user.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(commentService.unread(user));
    }
}
