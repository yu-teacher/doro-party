package com.doro.party.domain.pin.note;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.note.PrivateNoteDtos.PrivateNoteRequest;
import com.doro.party.domain.pin.note.PrivateNoteDtos.PrivateNoteResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "4. Private notes (사적 메모)", description = "쓴 사람에게만 보이는 핀 메모")
@RestController
@RequestMapping("/api/v1/maps/{mapId}")
@RequiredArgsConstructor
public class PrivateNoteController {

    private final PrivateNoteService noteService;

    @Operation(summary = "이 지도에서 내가 남긴 사적 메모 전체 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping("/private-notes")
    public ApiResponse<List<PrivateNoteResponse>> listMine(@PathVariable("mapId") UUID mapId, @CurrentDoroUser DoroUser doroUser) {
        return ApiResponse.success(noteService.listMine(mapId, doroUser));
    }

    @Operation(summary = "사적 메모 저장 (Guard: viewer 이상)", description = "핀마다 사용자 한 명에 메모 하나. 있으면 덮어쓴다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @RequestMapping(value = "/pins/{pinId}/private-note", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<PrivateNoteResponse> save(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @Valid @RequestBody PrivateNoteRequest request
    ) {
        return ApiResponse.success(noteService.save(mapId, pinId, doroUser, request.body()));
    }

    @Operation(summary = "사적 메모 삭제 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @DeleteMapping("/pins/{pinId}/private-note")
    public ApiResponse<Void> delete(@PathVariable("mapId") UUID mapId, @PathVariable("pinId") UUID pinId, @CurrentDoroUser DoroUser doroUser) {
        noteService.delete(mapId, pinId, doroUser);
        return ApiResponse.success();
    }
}
