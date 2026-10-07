package com.doro.party.domain.pin.photo;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.domain.pin.photo.PhotoDtos.PhotoContent;
import com.doro.party.domain.pin.photo.PhotoDtos.PhotoResponse;
import com.doro.party.infra.guard.PartyGuard;
import com.hunnit_beasts.doro.sdk.annotation.CurrentDoroUser;
import com.hunnit_beasts.doro.sdk.annotation.DoroGuard;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 사진은 비공개 스토리지에 있고, 지도를 볼 권한이 확인된 요청에만 이 컨트롤러가 내려준다. */
@Tag(name = "5. Photos (사진)", description = "핀에 붙이는 사진")
@RestController
@RequestMapping("/api/v1/maps/{mapId}/pins/{pinId}/photos")
@RequiredArgsConstructor
public class PhotoController {

    /** 브라우저가 이 사용자의 사진을 잠시 캐시해도 되지만, 공유 캐시(프록시)에는 남지 않게 private 으로 둔다. */
    private static final Duration BROWSER_CACHE = Duration.ofHours(24);

    private final PhotoService photoService;

    @Operation(summary = "사진 올리기 (Guard: editor 이상, 내가 꽂은 핀 또는 지도 주인)", description = "multipart 의 file. JPEG/PNG/WebP 만, 파일 내용으로 형식을 판별한다")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhotoResponse> upload(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @CurrentDoroUser DoroUser doroUser,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(photoService.upload(mapId, pinId, doroUser, file));
    }

    @Operation(summary = "핀의 사진 목록 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping
    public ApiResponse<List<PhotoResponse>> list(@PathVariable("mapId") UUID mapId, @PathVariable("pinId") UUID pinId) {
        return ApiResponse.success(photoService.list(mapId, pinId));
    }

    @Operation(summary = "사진 내용 (Guard: viewer 이상)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.VIEWER)
    @GetMapping("/{photoId}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @PathVariable("photoId") UUID photoId
    ) {
        PhotoContent content = photoService.open(mapId, pinId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.stream()));
    }

    @Operation(summary = "사진 삭제 (Guard: editor 이상, 내가 올린 사진 또는 지도 주인)")
    @DoroGuard(namespace = PartyGuard.MAP, object = "#mapId", relation = PartyGuard.EDITOR)
    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> delete(
            @PathVariable("mapId") UUID mapId,
            @PathVariable("pinId") UUID pinId,
            @PathVariable("photoId") UUID photoId,
            @CurrentDoroUser DoroUser doroUser
    ) {
        photoService.delete(mapId, pinId, photoId, doroUser);
        return ApiResponse.success();
    }
}
