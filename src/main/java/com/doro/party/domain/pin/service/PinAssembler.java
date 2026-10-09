package com.doro.party.domain.pin.service;

import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.dto.PinDtos.PinStats;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.comment.PinCommentRepository;
import com.doro.party.domain.pin.photo.PinPhotoRepository;
import com.doro.party.domain.pin.visit.VisitLogRepository;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 핀과 그 기록 요약(방문·사진·댓글)을 묶어 응답으로 만든다. 핀마다 조회하지 않고 한 번에 모아서 가져온다. */
@Component
@RequiredArgsConstructor
public class PinAssembler {

    private final VisitLogRepository visits;
    private final PinPhotoRepository photos;
    private final PinCommentRepository comments;
    private final PartyUserRepository users;

    public List<PinResponse> assemble(List<Pin> pins) {
        if (pins.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pins.stream().map(Pin::getId).toList();
        Map<UUID, VisitLogRepository.VisitStat> visitStats = visits.statsByPinIds(ids).stream()
                .collect(Collectors.toMap(VisitLogRepository.VisitStat::getPinId, stat -> stat));
        Map<UUID, PartyUser> authors = users.findAllById(pins.stream().map(Pin::getCreatedBy).distinct().toList()).stream()
                .collect(Collectors.toMap(PartyUser::getId, user -> user));
        Map<UUID, Long> photoCounts = new HashMap<>();
        photos.countsByPinIds(ids).forEach(count -> photoCounts.put(count.getPinId(), count.getCount()));
        Map<UUID, Long> commentCounts = new HashMap<>();
        comments.countsByPinIds(ids).forEach(count -> commentCounts.put(count.getPinId(), count.getCount()));

        return pins.stream().map(pin -> {
            VisitLogRepository.VisitStat visit = visitStats.get(pin.getId());
            PinStats stats = new PinStats(
                    visit == null ? 0 : visit.getVisitCount(),
                    visit == null ? null : visit.getLastVisitedOn(),
                    photoCounts.getOrDefault(pin.getId(), 0L),
                    commentCounts.getOrDefault(pin.getId(), 0L));
            PartyUser author = authors.get(pin.getCreatedBy());
            if (author == null) {
                throw new PartyException(ErrorCode.USER_NOT_FOUND);
            }
            return PinResponse.from(pin, stats, author);
        }).toList();
    }

    public PinResponse assemble(Pin pin) {
        return assemble(List.of(pin)).get(0);
    }
}
