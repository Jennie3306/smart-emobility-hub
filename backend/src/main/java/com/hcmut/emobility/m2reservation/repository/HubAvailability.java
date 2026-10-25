package com.hcmut.emobility.m2reservation.repository;

/** Projection: number of bookable vehicles at one hub (FR-M2-10). */
public interface HubAvailability {
    Long getHubId();
    long getAvailableCount();
}
