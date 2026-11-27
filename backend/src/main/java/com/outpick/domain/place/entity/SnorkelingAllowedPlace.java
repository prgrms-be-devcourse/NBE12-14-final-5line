package com.outpick.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only projection of currently verified snorkeling entry permission. */
@Entity
@Table(name = "snorkeling_allowed_place")
public class SnorkelingAllowedPlace {
    @Id
    @Column(name = "place_id")
    private Long placeId;
    protected SnorkelingAllowedPlace() { }
    public Long getPlaceId() { return placeId; }
}
