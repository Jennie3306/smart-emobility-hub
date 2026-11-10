package com.hcmut.emobility.m5charging.service;

import com.hcmut.emobility.m5charging.domain.ChargingSession;
import com.hcmut.emobility.m5charging.domain.PriorityLevel;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;

/** BR-M5-01, 02 and 09: one deterministic policy shared by the service and unit tests. */
public final class ChargingPriorityPolicy {

    private ChargingPriorityPolicy() { }

    public static int upcomingUseScore(OffsetDateTime expectedUseAt, OffsetDateTime now) {
        if (expectedUseAt == null || expectedUseAt.isBefore(now)) return 0;
        long minutes = Duration.between(now, expectedUseAt).toMinutes();
        if (minutes <= 30) return 100;
        if (minutes <= 60) return 70;
        if (minutes <= 120) return 40;
        return 0;
    }

    public static int waitingScore(OffsetDateTime waitingSince, OffsetDateTime now) {
        if (waitingSince == null || waitingSince.isAfter(now)) return 0;
        long waitedMinutes = Duration.between(waitingSince, now).toMinutes();
        return (int) Math.min(100, 2 * waitedMinutes);
    }

    public static double calculateScore(int batteryLevel, OffsetDateTime expectedUseAt,
                                         OffsetDateTime waitingSince, int failureBonus,
                                         PriorityLevel priority, OffsetDateTime now) {
        int batteryNeed = 100 - batteryLevel;
        int useUrgency = upcomingUseScore(expectedUseAt, now);
        int waitScore = waitingScore(waitingSince, now);
        double score = 0.5 * batteryNeed + 0.3 * useUrgency + 0.2 * waitScore + failureBonus;
        if (priority == PriorityLevel.HIGH) score += 30;
        return Math.round(score * 10.0) / 10.0;
    }

    /** URGENT is always first; otherwise higher score, then FIFO. */
    public static Comparator<ChargingSession> comparator() {
        return Comparator
                .comparing((ChargingSession s) -> s.getPriorityLevel() == PriorityLevel.URGENT ? 0 : 1)
                .thenComparing(ChargingSession::getPriorityScore, Comparator.reverseOrder())
                .thenComparing(ChargingSession::getCreatedAt);
    }
}
