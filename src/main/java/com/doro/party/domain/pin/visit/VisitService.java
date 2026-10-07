package com.doro.party.domain.pin.visit;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.service.PinAccess;
import com.doro.party.domain.pin.visit.VisitDtos.VisitRequest;
import com.doro.party.domain.pin.visit.VisitDtos.VisitResponse;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitLogRepository visits;
    private final PinAccess access;
    private final PartyUserService userService;
    private final PartyLimits limits;
    private final Clock clock;

    /**
     * 방문 기록을 남긴다. 편집 권한이 있으면 남의 핀에도 내 방문을 기록할 수 있다.
     * 핀을 만든 사람이 가고 싶던 곳(WISH)에 방문 기록을 남기면 그 핀은 다녀온 곳(VISITED)이 된다.
     */
    @Transactional
    public VisitResponse add(UUID mapId, UUID pinId, DoroUser doroUser, VisitRequest request) {
        PartyUser user = userService.getOrCreateUser(doroUser);
        Pin pin = access.requirePinForUpdate(mapId, pinId);
        if (request.visitedOn().isAfter(LocalDate.now(clock))) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "방문 날짜는 오늘 이후일 수 없습니다.");
        }
        if (visits.countByPinId(pinId) >= limits.maxVisitsPerPin()) {
            throw new PartyException(ErrorCode.LIMIT_EXCEEDED, "방문 기록은 핀마다 최대 " + limits.maxVisitsPerPin() + "개까지 남길 수 있습니다.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        VisitLog saved = visits.save(VisitLog.builder().pinId(pinId).userId(user.getId()).visitedOn(request.visitedOn()).note(note).build());
        if (pin.getCreatedBy().equals(user.getId()) && pin.getStatus() == PinStatus.WISH) {
            pin.markVisited();
        }
        log.info("Visit logged: visitId={}, pinId={}, userId={}", saved.getId(), pinId, user.getId());
        return VisitResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> list(UUID mapId, UUID pinId) {
        access.requirePin(mapId, pinId);
        return visits.findAllByPinIdOrderByVisitedOnDescCreatedAtDesc(pinId).stream().map(VisitResponse::from).toList();
    }

    /** 내가 남긴 기록이거나 내가 지도 주인이어야 지울 수 있다. */
    @Transactional
    public void delete(UUID mapId, UUID pinId, UUID visitId, DoroUser doroUser) {
        access.requirePin(mapId, pinId);
        VisitLog visit = visits.findByIdAndPinId(visitId, pinId).orElseThrow(() -> new PartyException(ErrorCode.VISIT_NOT_FOUND));
        access.requireRecordModifier(mapId, visit.getUserId(), doroUser.userId());
        visits.delete(visit);
    }
}
