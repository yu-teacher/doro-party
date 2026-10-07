package com.doro.party.domain.pin.note;

import com.doro.party.domain.pin.note.PrivateNoteDtos.PrivateNoteResponse;
import com.doro.party.domain.pin.service.PinAccess;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 사적 메모. 지도를 볼 수 있는 사람은 누구나 자기 메모를 남길 수 있고, 메모는 항상 "나" 의 것만 읽고 쓴다.
 * 조회·저장·삭제 모두 현재 사용자 ID 를 조건에 넣으므로 다른 사람의 메모에는 닿을 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class PrivateNoteService {

    private final PinPrivateNoteRepository notes;
    private final PinAccess access;
    private final PartyUserService userService;

    @Transactional(readOnly = true)
    public List<PrivateNoteResponse> listMine(UUID mapId, DoroUser doroUser) {
        return notes.findMine(mapId, doroUser.userId()).stream().map(PrivateNoteResponse::from).toList();
    }

    @Transactional
    public PrivateNoteResponse save(UUID mapId, UUID pinId, DoroUser doroUser, String body) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        access.requirePin(mapId, pinId);
        notes.upsert(pinId, user.getId(), body.strip());
        return notes.findById(keyOf(pinId, user.getId())).map(PrivateNoteResponse::from).orElseThrow();
    }

    @Transactional
    public void delete(UUID mapId, UUID pinId, DoroUser doroUser) {
        access.requirePin(mapId, pinId);
        notes.deleteMine(pinId, doroUser.userId());
    }

    private static PinPrivateNote.Key keyOf(UUID pinId, UUID userId) {
        return PinPrivateNote.Key.of(pinId, userId);
    }
}
