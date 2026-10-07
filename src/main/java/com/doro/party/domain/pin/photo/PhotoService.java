package com.doro.party.domain.pin.photo;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.photo.PhotoDtos.PhotoContent;
import com.doro.party.domain.pin.photo.PhotoDtos.PhotoResponse;
import com.doro.party.domain.pin.service.PinAccess;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.doro.party.infra.storage.ObjectStorage;
import com.doro.party.infra.storage.StorageCleanup;
import com.doro.party.infra.storage.StorageException;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PinPhotoRepository photos;
    private final PinAccess access;
    private final PartyUserService userService;
    private final ObjectStorage storage;
    private final StorageCleanup storageCleanup;
    private final PartyLimits limits;

    /** 내가 꽂은 핀이거나 내가 지도 주인이어야 사진을 붙일 수 있다. 형식은 파일 내용으로 판별한다. */
    @Transactional
    public PhotoResponse upload(UUID mapId, UUID pinId, DoroUser doroUser, MultipartFile file) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        Pin pin = access.requirePinForUpdate(mapId, pinId);
        access.requirePinModifier(pin, user.getId());
        if (file == null || file.isEmpty()) {
            throw new PartyException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > limits.maxPhotoBytes()) {
            throw new PartyException(ErrorCode.FILE_TOO_LARGE, "사진은 한 장에 최대 " + limits.maxPhotoBytes() / (1024 * 1024) + "MB 까지 올릴 수 있습니다.");
        }
        if (photos.countByPinId(pinId) >= limits.maxPhotosPerPin()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "사진은 핀마다 최대 " + limits.maxPhotosPerPin() + "장까지 붙일 수 있습니다.");
        }

        ImageSniffer.Detected image = detect(file);
        String key = "pins/%s/%s.%s".formatted(pinId, UUID.randomUUID(), image.extension());
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), image.contentType());
        } catch (IOException | StorageException e) {
            log.error("Photo upload to storage failed: pinId={}", pinId, e);
            throw new PartyException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        // 파일은 이미 올라갔으므로, 이 트랜잭션이 롤백되면 고아 파일이 남지 않게 지운다
        storageCleanup.deleteOnRollback(key);

        PinPhoto saved = photos.save(PinPhoto.builder()
                .pinId(pinId).uploadedBy(user.getId()).objectKey(key).contentType(image.contentType()).sizeBytes(file.getSize()).build());
        log.info("Photo uploaded: photoId={}, pinId={}, type={}, bytes={}", saved.getId(), pinId, image.contentType(), file.getSize());
        return PhotoResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> list(UUID mapId, UUID pinId) {
        access.requirePin(mapId, pinId);
        return photos.findAllByPinIdOrderByCreatedAtAsc(pinId).stream().map(PhotoResponse::from).toList();
    }

    /** 사진 내용을 연다. 지도를 볼 수 있는지는 컨트롤러가 확인했고, 여기서는 이 지도의 핀의 사진인지 확인한다. */
    @Transactional(readOnly = true)
    public PhotoContent open(UUID mapId, UUID pinId, UUID photoId) {
        access.requirePin(mapId, pinId);
        PinPhoto photo = photos.findByIdAndPinId(photoId, pinId).orElseThrow(() -> new PartyException(ErrorCode.PHOTO_NOT_FOUND));
        try {
            return new PhotoContent(photo.getContentType(), photo.getSizeBytes(), storage.open(photo.getObjectKey()));
        } catch (StorageException e) {
            log.error("Photo content could not be read: photoId={}", photoId, e);
            throw new PartyException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** 내가 올린 사진이거나 내가 지도 주인이어야 지울 수 있다. */
    @Transactional
    public void delete(UUID mapId, UUID pinId, UUID photoId, DoroUser doroUser) {
        access.requirePin(mapId, pinId);
        PinPhoto photo = photos.findByIdAndPinId(photoId, pinId).orElseThrow(() -> new PartyException(ErrorCode.PHOTO_NOT_FOUND));
        access.requireRecordModifier(mapId, photo.getUploadedBy(), doroUser.userId());
        photos.delete(photo);
        storageCleanup.deleteAfterCommit(List.of(photo.getObjectKey()));
    }

    private static ImageSniffer.Detected detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(ImageSniffer.HEADER_BYTES);
            return ImageSniffer.detect(Arrays.copyOf(header, header.length))
                    .orElseThrow(() -> new PartyException(ErrorCode.INVALID_FILE_TYPE));
        } catch (IOException e) {
            log.warn("Uploaded file could not be read", e);
            throw new PartyException(ErrorCode.INVALID_FILE_TYPE);
        }
    }
}
