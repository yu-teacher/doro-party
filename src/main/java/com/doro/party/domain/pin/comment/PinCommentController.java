package com.doro.party.domain.pin.comment;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.comment.PinCommentDtos.CommentRequest;
import com.doro.party.domain.pin.comment.PinCommentDtos.CommentResponse;
import com.doro.party.domain.pin.comment.PinCommentDtos.ReadRequest;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.annotation.DoroGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "3. Comments (핀 댓글)", description = "지도를 볼 수 있는 사람이 핀에 남기는 댓글과 읽음 표시")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/pins/{pinId}/comments")
@RequiredArgsConstructor
public class PinCommentController {

    private final PinCommentService commentService;

    @Operation(summary = "댓글 목록 (Guard: viewer 이상)", description = "오래된 순")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping
    public ApiResponse<List<CommentResponse>> list(@PathVariable("mapId") UUID mapId, @PathVariable("pinId") UUID pinId) {
        return ApiResponse.success(commentService.list(mapId, pinId));
    }

    @Operation(summary = "댓글 남기기 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @PostMapping
    public ApiResponse<CommentResponse> add(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody CommentRequest request
    ) {
        return ApiResponse.success(commentService.add(mapId, pinId, doroUser, request));
    }

    @Operation(summary = "댓글 고치기 (Guard: viewer 이상, 쓴 사람만)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @PatchMapping("/{commentId}")
    public ApiResponse<CommentResponse> edit(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @PathVariable("commentId") UUID commentId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody CommentRequest request
    ) {
        return ApiResponse.success(commentService.edit(mapId, pinId, commentId, doroUser, request));
    }

    @Operation(summary = "댓글 지우기 (Guard: viewer 이상, 쓴 사람 · 핀을 꽂은 사람 · 지도 주인)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @PathVariable("commentId") UUID commentId,
            @CurrentDoroUser DoroUser doroUser
    ) {
        commentService.delete(mapId, pinId, commentId, doroUser);
        return ApiResponse.success();
    }

    @Operation(summary = "여기까지 읽었어요 (Guard: viewer 이상)", description = "화면에 본 마지막 댓글 시각(upTo)까지를 읽은 것으로 표시한다. 시각은 뒤로 가지 않는다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @PostMapping("/read")
    public ApiResponse<Void> markRead(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody ReadRequest request
    ) {
        commentService.markRead(mapId, pinId, doroUser, request.upTo());
        return ApiResponse.success();
    }
}
