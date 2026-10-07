package com.doro.party.domain.pin.service;

import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.dto.PinDtos.PinStats;
import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.photo.PinPhotoRepository;
import com.doro.party.domain.pin.visit.VisitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 핀과 그 기록 요약(방문·사진)을 묶어 응답으로 만든다. 핀마다 조회하지 않고 한 번에 모아서 가져온다. */
@Component
@RequiredArgsConstructor
public class PinAssembler {

    private final VisitLogRepository visits;
    private final PinPhotoRepository photos;

    public List<PinResponse> assemble(List<Pin> pins) {
        if (pins.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pins.stream().map(Pin::getId).toList();
        Map<UUID, VisitLogRepository.VisitStat> visitStats = visits.statsByPinIds(ids).stream()
                .collect(Collectors.toMap(VisitLogRepository.VisitStat::getPinId, stat -> stat));
        Map<UUID, Long> photoCounts = new HashMap<>();
        photos.countsByPinIds(ids).forEach(count -> photoCounts.put(count.getPinId(), count.getCount()));

        return pins.stream().map(pin -> {
            VisitLogRepository.VisitStat visit = visitStats.get(pin.getId());
            PinStats stats = new PinStats(
                    visit == null ? 0 : visit.getVisitCount(),
                    visit == null ? null : visit.getLastVisitedOn(),
                    photoCounts.getOrDefault(pin.getId(), 0L));
            return PinResponse.from(pin, stats);
        }).toList();
    }

    public PinResponse assemble(Pin pin) {
        return assemble(List.of(pin)).get(0);
    }
}
