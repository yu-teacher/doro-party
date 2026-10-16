package com.doro.party.domain.user.sync;

import com.hunnit_beasts.doro.sdk.sync.SyncCursorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** SDK 탈퇴 동기화의 진행 위치(기준 시각)를 도로 파티 DB({@code iam_sync_state})에 보관한다. 기준 시각은 앞으로만 움직인다. */
@Component
@RequiredArgsConstructor
public class PartySyncCursorStore implements SyncCursorStore {

    private final IamSyncStateRepository states;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> get(String name) {
        return states.findById(name).map(IamSyncState::getCursorAt);
    }

    @Override
    @Transactional
    public void advance(String name, Instant cursor) {
        IamSyncState state = states.findById(name).orElseGet(() -> new IamSyncState(name, cursor));
        if (cursor.isAfter(state.getCursorAt())) {
            state.advanceTo(cursor);
        }
        states.save(state);
    }
}
