package com.doro.party.domain.user.sync;

import com.doro.party.infra.guard.GuardTuples;
import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.infra.storage.ObjectStorage;
import com.doro.party.infra.storage.StorageException;
import com.hunnit_beasts.doro.sdk.exception.DoroGuardWriteFailedException;
import com.hunnit_beasts.doro.sdk.sync.DeletedUserHandler;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersClient;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersClient.DeletedUser;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersClient.DeletedUsersPage;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersProperties;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersSync;
import com.hunnit_beasts.doro.sdk.sync.SyncCursorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** 탈퇴 동기화의 배선(SDK 빈 등록·기준 시각 저장)과, 사진 파일·Guard 삭제가 중간에 실패했을 때 다음 시도가 이어서 끝내는지 검증한다. */
class DeletedAccountSyncTest extends DeletedAccountTestSupport {

    /** SDK 동기화가 기준 시각을 저장하는 이름(기본값). */
    private static final String STATE_NAME = "iam-deleted-users";

    @Autowired private DeletedAccountAnonymizer anonymizer;
    @Autowired private DeletedUserHandler handler;
    @Autowired private DeletedUsersSync sync;
    @Autowired private DeletedUsersProperties properties;
    @Autowired private SyncCursorStore cursors;
    @Autowired private IamSyncStateRepository states;
    @MockitoBean private DeletedUsersClient iam;
    @MockitoSpyBean private ObjectStorage spiedStorage;
    @MockitoSpyBean private GuardTuples spiedTuples;

    @BeforeEach
    void resetState() {
        reset(iam, spiedStorage, spiedTuples);
        states.deleteAll();
    }

    // ---------------- 배선 ----------------

    @Test
    @DisplayName("탈퇴 동기화가 켜져 있고(기본 활성), 핸들러와 기준 시각 저장소가 등록되어 익명화를 호출한다")
    void wiring() throws Exception {
        TestUser user = newUser();
        createMap(user, "지도");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(cursors).isInstanceOf(PartySyncCursorStore.class);
        assertThat(handler.onDeleted(user.id(), Instant.now())).isTrue();
        assertThat(partyUser(user.id()).get("deleted_at")).isNotNull();
    }

    @Test
    @DisplayName("동기화는 Doro 목록의 사용자를 익명화하고, 도로 파티 사용자가 아닌 ID 는 무시하며, 기준 시각을 저장한다")
    void syncAnonymizesListedUsersAndStoresTheCursor() throws Exception {
        TestUser user = newUser();
        createMap(user, "지도");
        Instant deletedAt = Instant.now();
        when(iam.fetchDeletedUsers(any(), anyInt())).thenReturn(new DeletedUsersPage(
                List.of(new DeletedUser(user.id(), deletedAt), new DeletedUser(UUID.randomUUID(), deletedAt)), deletedAt));

        assertThat(sync.sync()).isEqualTo(1);

        assertThat(partyUser(user.id()).get("deleted_at")).isNotNull();
        assertThat(states.findById(STATE_NAME)).isPresent();
    }

    // ---------------- 기준 시각 저장소 ----------------

    @Test
    @DisplayName("기준 시각은 앞으로만 움직인다")
    void cursorOnlyAdvances() {
        Instant t = Instant.parse("2026-10-01T00:00:00Z");

        assertThat(cursors.get("cursor-test")).isEmpty();
        cursors.advance("cursor-test", t);
        cursors.advance("cursor-test", t.minusSeconds(3600));
        assertThat(cursors.get("cursor-test")).contains(t);
        cursors.advance("cursor-test", t.plusSeconds(60));
        assertThat(cursors.get("cursor-test")).contains(t.plusSeconds(60));
        cursors.advance("other-name", t.minusSeconds(1));
        assertThat(cursors.get("other-name")).contains(t.minusSeconds(1));
        assertThat(cursors.get("cursor-test")).contains(t.plusSeconds(60));
    }

    // ---------------- 부분 실패 후 재시도 ----------------

    @Test
    @DisplayName("사진 파일 삭제가 중간에 실패하면 아무것도 확정되지 않고, 다음 시도가 이어서 모두 지운다")
    void retriesAfterPartialPhotoFailure() throws Exception {
        TestUser user = newUser();
        String map = createMap(user, "공유 지도");
        TestUser friend = newUser();
        befriend(user, friend);
        shareMap(user, map, friend, "VIEWER").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String pin = pin(user, map, "핀");
        String key1 = objectKeyOf(upload(user, map, pin));
        String key2 = objectKeyOf(upload(user, map, pin));

        // 첫 파일은 지우고 두 번째에서 실패한다
        doCallRealMethod().doThrow(new StorageException("minio down", null)).when(spiedStorage).delete(anyString());
        assertThatThrownBy(() -> anonymizer.anonymize(user.id(), Instant.now())).isInstanceOf(StorageException.class);

        assertThat(partyUser(user.id()).get("deleted_at")).as("실패했으니 처리 완료로 표시되지 않는다").isNull();
        assertThat(count("select count(*) from pin_photos where uploaded_by = ?", user.id())).as("기록은 그대로").isEqualTo(2);
        assertThat(count("select count(*) from friendships where user_low_id = ? or user_high_id = ?", user.id(), user.id())).isEqualTo(1);
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, user.id())).isTrue();
        assertThat(stored(key1) ^ stored(key2)).as("첫 파일만 지워졌다").isTrue();

        reset(spiedStorage);
        assertThat(anonymizer.anonymize(user.id(), Instant.now())).isTrue();

        assertThat(partyUser(user.id()).get("deleted_at")).isNotNull();
        assertThat(stored(key1)).isFalse();
        assertThat(stored(key2)).isFalse();
        assertThat(count("select count(*) from pin_photos where uploaded_by = ?", user.id())).isZero();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, user.id())).isFalse();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.VIEWER, friend.id())).isTrue();
    }

    @Test
    @DisplayName("Guard 튜플 삭제가 실패하면 예외로 알리고 DB 도 되돌아가며, 다음 시도가 끝까지 처리한다")
    void retriesAfterGuardFailure() throws Exception {
        TestUser user = newUser();
        String map = createMap(user, "지도");
        TestUser friend = newUser();
        befriend(user, friend);
        shareMap(user, map, friend, "VIEWER").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        doThrow(new DoroGuardWriteFailedException("guard down", null)).when(spiedTuples)
                .deleteOrThrow(anyString(), anyString(), anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> anonymizer.anonymize(user.id(), Instant.now())).isInstanceOf(DoroGuardWriteFailedException.class);

        assertThat(partyUser(user.id()).get("deleted_at")).isNull();
        assertThat(count("select count(*) from friendships where user_low_id = ? or user_high_id = ?", user.id(), user.id())).isEqualTo(1);
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, user.id())).isTrue();

        reset(spiedTuples);
        assertThat(anonymizer.anonymize(user.id(), Instant.now())).isTrue();

        assertThat(partyUser(user.id()).get("deleted_at")).isNotNull();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.OWNER, user.id())).isFalse();
        assertThat(has(PartyGuard.FRIENDS, friend.id().toString(), PartyGuard.FRIEND, user.id())).isFalse();
        assertThat(has(PartyGuard.MAP, map, PartyGuard.VIEWER, friend.id())).isTrue();
    }

    @Test
    @DisplayName("동기화 중 처리가 실패해도 예약 실행은 예외를 밖으로 내지 않고, 다음 실행이 같은 사용자부터 다시 처리한다")
    void syncResumesAfterHandlerFailure() throws Exception {
        TestUser user = newUser();
        String map = createMap(user, "지도");
        Instant deletedAt = Instant.now();
        when(iam.fetchDeletedUsers(any(), anyInt())).thenReturn(new DeletedUsersPage(List.of(new DeletedUser(user.id(), deletedAt)), deletedAt));
        doThrow(new DoroGuardWriteFailedException("guard down", null)).when(spiedTuples)
                .deleteOrThrow(eq(PartyGuard.MAP), anyString(), anyString(), anyString(), anyString(), any());

        sync.scheduledRun();

        assertThat(partyUser(user.id()).get("deleted_at")).isNull();
        assertThat(states.findById(STATE_NAME)).as("실패한 사용자 앞으로는 기준 시각이 나아가지 않는다").isEmpty();

        reset(spiedTuples);
        assertThat(sync.sync()).isEqualTo(1);
        assertThat(partyUser(user.id()).get("deleted_at")).isNotNull();
        assertThat(count("select count(*) from party_maps where id = ?::uuid", map)).isZero();
    }
}
