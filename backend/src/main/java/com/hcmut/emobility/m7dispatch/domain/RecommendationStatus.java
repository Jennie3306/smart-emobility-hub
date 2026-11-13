package com.hcmut.emobility.m7dispatch.domain;

/**
 * Lifecycle of a redistribution proposal (FR-M7-02).
 *   PENDING  -> APPROVED  (Operator approves; every requested vehicle got a task)
 *   PENDING  -> PARTIAL   (approved, but only some vehicles could be sent; approving again sends more)
 *   PARTIAL  -> APPROVED
 *   PENDING  -> REJECTED  (Operator rejects; no task is created)
 */
public enum RecommendationStatus {
    PENDING, APPROVED, PARTIAL, REJECTED;

    public boolean isOpen() {
        return this == PENDING || this == PARTIAL;
    }
}
