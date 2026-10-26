package com.hcmut.emobility.m1monitoring.domain;

/**
 * Ket qua dem theo trang thai cua mot hub, lay bang mot truy van gop
 * thay vi nap toan bo ban ghi roi dem trong bo nho.
 */
public record HubCounts(
        long hubId,
        long totalSlots, long usedSlots, long freeSlots,
        long operationalPoints, long pointsInUse, long freePoints) {

    public static HubCounts empty(long hubId) {
        return new HubCounts(hubId, 0, 0, 0, 0, 0, 0);
    }
}
