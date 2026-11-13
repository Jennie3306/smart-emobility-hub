package com.hcmut.emobility.m5charging.service;

import com.hcmut.emobility.m5charging.domain.ChargingSession;
import com.hcmut.emobility.m5charging.domain.PriorityLevel;
import com.hcmut.emobility.m5charging.domain.VehicleKind;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChargingPriorityPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-10T10:00:00+07:00");

    @Test
    void batteryBoundaryValues19_20_21FollowBva() {
        double at19 = ChargingPriorityPolicy.calculateScore(19, null, NOW, 0, PriorityLevel.NORMAL, NOW);
        double at20 = ChargingPriorityPolicy.calculateScore(20, null, NOW, 0, PriorityLevel.NORMAL, NOW);
        double at21 = ChargingPriorityPolicy.calculateScore(21, null, NOW, 0, PriorityLevel.NORMAL, NOW);
        assertEquals(40.5, at19);
        assertEquals(40.0, at20);
        assertEquals(39.5, at21);
    }

    @Test
    void expectedUseBandsAre100_70_40_0() {
        assertEquals(100, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(30), NOW));
        assertEquals(70, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(31), NOW));
        assertEquals(70, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(60), NOW));
        assertEquals(40, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(61), NOW));
        assertEquals(40, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(120), NOW));
        assertEquals(0, ChargingPriorityPolicy.upcomingUseScore(NOW.plusMinutes(121), NOW));
    }

    @Test
    void waitingScoreCapsAt100() {
        assertEquals(98, ChargingPriorityPolicy.waitingScore(NOW.minusMinutes(49), NOW));
        assertEquals(100, ChargingPriorityPolicy.waitingScore(NOW.minusMinutes(50), NOW));
        assertEquals(100, ChargingPriorityPolicy.waitingScore(NOW.minusMinutes(90), NOW));
    }

    @Test
    void highPriorityAddsThirtyPointsAndUrgentRanksFirst() {
        double normal = ChargingPriorityPolicy.calculateScore(30, null, NOW, 0, PriorityLevel.NORMAL, NOW);
        double high = ChargingPriorityPolicy.calculateScore(30, null, NOW, 0, PriorityLevel.HIGH, NOW);
        assertEquals(30.0, high - normal);

        ChargingSession normalSession = session(1L, PriorityLevel.NORMAL, 99);
        ChargingSession urgentSession = session(2L, PriorityLevel.URGENT, 1);
        List<ChargingSession> result = List.of(normalSession, urgentSession).stream()
                .sorted(ChargingPriorityPolicy.comparator()).toList();
        assertEquals(2L, result.get(0).getVehicleId());
    }

    @Test
    void equalScoreUsesFirstCreatedAsFifoTieBreak() {
        ChargingSession earlier = ChargingSession.request(1L, 1L, null, VehicleKind.RENTAL,
                1L, 50, null, NOW.minusMinutes(2));
        ChargingSession later = ChargingSession.request(1L, 1L, null, VehicleKind.RENTAL,
                2L, 50, null, NOW.minusMinutes(1));
        earlier.enqueue(NOW.minusMinutes(2));
        later.enqueue(NOW.minusMinutes(1));
        earlier.setPriorityScore(50);
        later.setPriorityScore(50);
        List<ChargingSession> result = List.of(later, earlier).stream()
                .sorted(ChargingPriorityPolicy.comparator()).toList();
        assertEquals(1L, result.get(0).getVehicleId());
    }

    private ChargingSession session(Long vehicleId, PriorityLevel priority, double score) {
        ChargingSession session = ChargingSession.request(1L, 1L, null, VehicleKind.RENTAL,
                vehicleId, 50, null, NOW);
        session.enqueue(NOW);
        session.setPriorityLevel(priority);
        session.setPriorityScore(score);
        return session;
    }

    @org.junit.jupiter.api.Test
    void expectedUseAlreadyPassedIsMostUrgent() {
        // Review PR #11: a vehicle whose expected use time has passed is needed now, so U = 100, not 0.
        assertEquals(100, ChargingPriorityPolicy.upcomingUseScore(NOW, NOW));
        assertEquals(100, ChargingPriorityPolicy.upcomingUseScore(NOW.minusMinutes(5), NOW));
        assertEquals(0, ChargingPriorityPolicy.upcomingUseScore(null, NOW));
    }
}
