package com.doro.party.domain.pin.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 지도 위에 직접 꽂은 핀. 외부 장소 데이터가 아니라 좌표와 내 기록만 저장한다. */
@DynamicUpdate
@Entity
@Table(name = "pins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "map_id", nullable = false, updatable = false)
    private UUID mapId;

    /** 핀을 꽂은 사람. 공유 지도에서 작성자를 구분하는 기준이다. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "shared_memo", length = 2000)
    private String sharedMemo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PinStatus status;

    @Column
    private Integer rating;

    @ElementCollection
    @CollectionTable(name = "pin_tags", joinColumns = @JoinColumn(name = "pin_id"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Pin(UUID mapId, UUID createdBy, double lat, double lng, String name, String sharedMemo,
                PinStatus status, Integer rating, Set<String> tags) {
        this.mapId = mapId;
        this.createdBy = createdBy;
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.sharedMemo = sharedMemo;
        this.status = status;
        this.rating = rating;
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public void update(double lat, double lng, String name, String sharedMemo, PinStatus status, Integer rating, Set<String> newTags) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.sharedMemo = sharedMemo;
        this.status = status;
        this.rating = rating;
        this.tags.retainAll(newTags);
        this.tags.addAll(newTags);
    }
}
