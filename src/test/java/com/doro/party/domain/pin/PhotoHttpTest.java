package com.doro.party.domain.pin;

import com.doro.party.infra.guard.PartyGuard;
import com.doro.party.infra.storage.ObjectStorage;
import com.doro.party.infra.storage.StorageException;
import com.doro.party.support.PartyHttpTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 사진: 파일 내용으로 형식 판별, 권한, 비공개 서빙, 고아 파일 정리를 실제 MinIO 로 검증한다. */
class PhotoHttpTest extends PartyHttpTestBase {

    /** 1x1 PNG */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    static final byte[] JPEG = concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, "JFIF-test-bytes".getBytes(StandardCharsets.US_ASCII));
    static final byte[] WEBP = concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[]{0x10, 0, 0, 0}, "WEBPVP8 ".getBytes(StandardCharsets.US_ASCII));

    @Autowired private ObjectStorage storage;
    @Autowired private JdbcTemplate jdbc;

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private ResultActions upload(TestUser user, String mapId, String pinId, String filename, String declaredType, byte[] bytes) throws Exception {
        return mockMvc.perform(user.sign(multipart("/api/v1/maps/{m}/pins/{p}/photos", mapId, pinId)
                .file(new MockMultipartFile("file", filename, declaredType, bytes))));
    }

    private String uploadOk(TestUser user, String mapId, String pinId, byte[] bytes) throws Exception {
        String body = upload(user, mapId, pinId, "photo.bin", "application/octet-stream", bytes)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String objectKeyOf(String photoId) {
        return jdbc.queryForObject("select object_key from pin_photos where id = ?::uuid", String.class, photoId);
    }

    private boolean stored(String key) {
        try (InputStream ignored = storage.open(key)) {
            return true;
        } catch (StorageException e) {
            return false;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String newMapAndPin(TestUser owner) throws Exception {
        String mapId = createMap(owner, "사진");
        return mapId + "/" + createPin(owner, mapId, pinBody("사진 핀", 37.5, 127.0, null, null, null));
    }

    @Test
    @DisplayName("사진을 올리면 목록에 나오고, 내용은 올린 그대로 안전한 헤더와 함께 내려온다")
    void uploadListAndServe() throws Exception {
        TestUser owner = newUser();
        String[] ids = newMapAndPin(owner).split("/");
        String mapId = ids[0], pinId = ids[1];

        String photoId = uploadOk(owner, mapId, pinId, PNG);

        send(owner, get("/api/v1/maps/{m}/pins/{p}/photos", mapId, pinId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.data[0].sizeBytes").value(PNG.length));
        byte[] served = send(owner, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", mapId, pinId, photoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);
        send(owner, get("/api/v1/maps/{m}/pins", mapId)).andExpect(jsonPath("$.data[0].photoCount").value(1));
        // 파일은 비공개 스토리지에 있고 DB 에는 키만 있다
        assertThat(stored(objectKeyOf(photoId))).isTrue();
    }

    @Test
    @DisplayName("JPEG·WebP 도 받고, 클라이언트가 선언한 형식이 아니라 파일 내용으로 형식을 정한다")
    void typeIsDecidedByContent() throws Exception {
        TestUser owner = newUser();
        String[] ids = newMapAndPin(owner).split("/");

        // HTML 이라고 우겨도 내용이 JPEG 이면 JPEG 로 저장된다
        upload(owner, ids[0], ids[1], "x.html", "text/html", JPEG)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentType").value("image/jpeg"));
        upload(owner, ids[0], ids[1], "x.jpg", "image/jpeg", WEBP)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentType").value("image/webp"));
    }

    @Test
    @DisplayName("사진이 아닌 파일은 이름·선언과 무관하게 400: 텍스트, HTML, SVG, GIF, 빈 파일")
    void nonImagesAreRejected() throws Exception {
        TestUser owner = newUser();
        String[] ids = newMapAndPin(owner).split("/");
        record Bad(String name, String declared, byte[] bytes) {}
        List<Bad> bad = List.of(
                new Bad("evil.png", "image/png", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)),
                new Bad("evil.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)),
                new Bad("anim.gif", "image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)),
                new Bad("note.png", "image/png", "그냥 텍스트".getBytes(StandardCharsets.UTF_8)),
                new Bad("empty.png", "image/png", new byte[0]));

        for (Bad file : bad) {
            upload(owner, ids[0], ids[1], file.name(), file.declared(), file.bytes())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD-400-01"));
        }
        send(owner, get("/api/v1/maps/{m}/pins/{p}/photos", ids[0], ids[1])).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("접근 제어: 비로그인은 401, 지도를 볼 수 없으면 403, viewer 는 보기만, editor 는 자기 핀에만 올릴 수 있다")
    void photoPermissions() throws Exception {
        TestUser owner = newUser();
        TestUser editor = newUser();
        TestUser viewer = newUser();
        TestUser stranger = newUser();
        String[] ids = newMapAndPin(owner).split("/");
        String mapId = ids[0], ownerPin = ids[1];
        grant(mapId, PartyGuard.EDITOR, editor);
        grant(mapId, PartyGuard.VIEWER, viewer);
        String editorPin = createPin(editor, mapId, pinBody("편집자 핀", 37.6, 127.1, null, null, null));
        String photoId = uploadOk(owner, mapId, ownerPin, PNG);

        // 비로그인: 올리기도 보기도 401
        mockMvc.perform(multipart("/api/v1/maps/{m}/pins/{p}/photos", mapId, ownerPin).file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", mapId, ownerPin, photoId)).andExpect(status().isUnauthorized());

        // 지도를 볼 수 없는 사람: 목록·내용·올리기·지우기 모두 403
        send(stranger, get("/api/v1/maps/{m}/pins/{p}/photos", mapId, ownerPin)).andExpect(status().isForbidden());
        send(stranger, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", mapId, ownerPin, photoId)).andExpect(status().isForbidden());
        upload(stranger, mapId, ownerPin, "a.png", "image/png", PNG).andExpect(status().isForbidden());
        send(stranger, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", mapId, ownerPin, photoId)).andExpect(status().isForbidden());

        // viewer: 보기만
        send(viewer, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", mapId, ownerPin, photoId)).andExpect(status().isOk());
        upload(viewer, mapId, ownerPin, "a.png", "image/png", PNG).andExpect(status().isForbidden());
        send(viewer, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", mapId, ownerPin, photoId)).andExpect(status().isForbidden());

        // editor: 남의 핀에는 못 올리고, 자기 핀에는 올린다. 남이 올린 사진은 못 지운다
        upload(editor, mapId, ownerPin, "a.png", "image/png", PNG).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH-403-01"));
        String editorPhoto = uploadOk(editor, mapId, editorPin, PNG);
        send(editor, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", mapId, ownerPin, photoId)).andExpect(status().isForbidden());

        // 주인은 누구의 사진이든 지울 수 있다
        send(owner, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", mapId, editorPin, editorPhoto)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("IDOR: 내 지도 경로로 다른 지도 핀의 사진을 읽거나 지울 수 없다")
    void photoIdor() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        String aliceMap = createMap(alice, "앨리스");
        String[] bobIds = newMapAndPin(bob).split("/");
        String bobPhoto = uploadOk(bob, bobIds[0], bobIds[1], PNG);
        String alicePin = createPin(alice, aliceMap, pinBody("앨리스 핀", 37.5, 127.0, null, null, null));

        send(alice, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", aliceMap, bobIds[1], bobPhoto)).andExpect(status().isNotFound());
        send(alice, get("/api/v1/maps/{m}/pins/{p}/photos", aliceMap, bobIds[1])).andExpect(status().isNotFound());
        send(alice, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", aliceMap, bobIds[1], bobPhoto)).andExpect(status().isNotFound());
        // 같은 지도 안이라도 다른 핀의 사진 ID 로는 접근할 수 없다
        send(alice, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", aliceMap, alicePin, bobPhoto)).andExpect(status().isNotFound());
        assertThat(stored(objectKeyOf(bobPhoto))).isTrue();
    }

    @Test
    @DisplayName("사진을 지우면 스토리지의 파일도 지워진다")
    void deletingAPhotoRemovesTheObject() throws Exception {
        TestUser owner = newUser();
        String[] ids = newMapAndPin(owner).split("/");
        String photoId = uploadOk(owner, ids[0], ids[1], PNG);
        String key = objectKeyOf(photoId);
        assertThat(stored(key)).isTrue();

        send(owner, delete("/api/v1/maps/{m}/pins/{p}/photos/{id}", ids[0], ids[1], photoId)).andExpect(status().isOk());

        assertThat(stored(key)).isFalse();
        send(owner, get("/api/v1/maps/{m}/pins/{p}/photos/{id}/content", ids[0], ids[1], photoId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("핀이나 지도를 지우면 그 안의 사진 파일이 스토리지에 남지 않는다")
    void deletingPinOrMapRemovesObjects() throws Exception {
        TestUser owner = newUser();
        String[] ids = newMapAndPin(owner).split("/");
        String secondPin = createPin(owner, ids[0], pinBody("둘째", 37.6, 127.1, null, null, null));
        String keyOfFirst = objectKeyOf(uploadOk(owner, ids[0], ids[1], PNG));
        String keyOfSecondA = objectKeyOf(uploadOk(owner, ids[0], secondPin, JPEG));
        String keyOfSecondB = objectKeyOf(uploadOk(owner, ids[0], secondPin, WEBP));

        send(owner, delete("/api/v1/maps/{m}/pins/{p}", ids[0], ids[1])).andExpect(status().isOk());
        assertThat(stored(keyOfFirst)).as("지운 핀의 사진").isFalse();
        assertThat(stored(keyOfSecondA)).as("남은 핀의 사진은 그대로").isTrue();

        send(owner, delete("/api/v1/maps/{m}", ids[0])).andExpect(status().isOk());
        assertThat(stored(keyOfSecondA)).isFalse();
        assertThat(stored(keyOfSecondB)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from pin_photos where object_key in (?, ?, ?)", Long.class, keyOfFirst, keyOfSecondA, keyOfSecondB)).isZero();
    }
}
