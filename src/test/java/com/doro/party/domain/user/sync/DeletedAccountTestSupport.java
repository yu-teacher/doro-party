package com.doro.party.domain.user.sync;

import com.doro.party.infra.storage.ObjectStorage;
import com.doro.party.infra.storage.StorageException;
import com.doro.party.support.PartyHttpTestBase;
import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 탈퇴 정리 테스트의 공통 도우미: 실제 API 로 지도·핀·사진·기록을 만들고 DB·스토리지·Guard 로 결과를 확인한다. */
abstract class DeletedAccountTestSupport extends PartyHttpTestBase {

    /** 1x1 PNG */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectStorage storage;
    @Autowired protected DoroGuardClient guard;

    protected String upload(TestUser user, String mapId, String pinId) throws Exception {
        String body = mockMvc.perform(user.sign(multipart("/api/v1/maps/{m}/pins/{p}/photos", mapId, pinId)
                        .file(new MockMultipartFile("file", "p.bin", "application/octet-stream", PNG))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    protected String objectKeyOf(String photoId) {
        return jdbc.queryForObject("select object_key from pin_photos where id = ?::uuid", String.class, photoId);
    }

    protected boolean stored(String key) {
        try (InputStream ignored = storage.open(key)) {
            return true;
        } catch (StorageException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void note(TestUser user, String mapId, String pinId, String body) throws Exception {
        send(user, put("/api/v1/maps/{m}/pins/{p}/private-note", mapId, pinId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}")).andExpect(status().isOk());
    }

    protected void visit(TestUser user, String mapId, String pinId) throws Exception {
        send(user, post("/api/v1/maps/{m}/pins/{p}/visits", mapId, pinId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitedOn\":\"2026-01-02\",\"note\":\"다녀옴\"}")).andExpect(status().isOk());
    }

    protected void comment(TestUser user, String mapId, String pinId, String body) throws Exception {
        send(user, post("/api/v1/maps/{m}/pins/{p}/comments", mapId, pinId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}")).andExpect(status().isOk());
    }

    protected void markRead(TestUser user, String mapId, String pinId) throws Exception {
        send(user, post("/api/v1/maps/{m}/pins/{p}/comments/read", mapId, pinId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"upTo\":\"" + java.time.Instant.now().plusSeconds(5) + "\"}")).andExpect(status().isOk());
    }

    protected String pin(TestUser user, String mapId, String name) throws Exception {
        return createPin(user, mapId, pinBody(name, 37.5, 127.0, null, null, null));
    }

    protected String createGroup(TestUser owner, String name) throws Exception {
        String body = send(owner, post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    protected void joinGroup(TestUser owner, String groupId, TestUser user) throws Exception {
        String body = send(owner, post("/api/v1/groups/{g}/invite", groupId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.data.code");
        send(user, post("/api/v1/groups/invite/{c}/join", code)).andExpect(status().isOk());
    }

    protected void shareToGroup(TestUser owner, String mapId, String groupId) throws Exception {
        send(owner, put("/api/v1/maps/{m}/groups/{g}", mapId, groupId)).andExpect(status().isOk());
    }

    protected void friendAccess(TestUser owner, String mapId, String access) throws Exception {
        send(owner, put("/api/v1/maps/{m}/friend-access", mapId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"access\":\"" + access + "\"}")).andExpect(status().isOk());
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected Map<String, Object> partyUser(UUID id) {
        return jdbc.queryForMap("select * from party_users where id = ?", id);
    }

    protected boolean has(String ns, String object, String relation, UUID user) {
        return guard.check(ns, object, relation, user.toString());
    }
}
