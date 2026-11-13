package com.hcmut.emobility.m5charging.domain;

public enum ChargingSessionStatus {
    REQUESTED, QUEUED, SCHEDULED, CHARGING, COMPLETED, INTERRUPTED, CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == REQUESTED || this == QUEUED || this == SCHEDULED
                || this == CHARGING || this == INTERRUPTED;
    }
}
