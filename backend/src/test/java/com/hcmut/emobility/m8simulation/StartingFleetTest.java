package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StartingFleetTest {
    @Test void overridesAvailableVehiclesWithoutChangingTheSourceSnapshot() {
        var source = new Snapshot("live", Instant.EPOCH, List.of(new Hub(1, "Metro", 10, 2)),
                List.of(new Vehicle(1, 1L, 90, "AVAILABLE"), new Vehicle(2, 1L, 10, "AVAILABLE")),
                List.of(), "MANUAL_SCENARIO", null, List.of(new CapacityState(1, 10, 2, 8, 2, 0)));
        var revised = StartingFleet.override(source, "1:5");
        assertTrue(revised.hypotheticalFleet());
        assertNotEquals(source.id(), revised.id());
        assertEquals(2, source.vehicles().size());
        assertEquals(6, revised.vehicles().size());
        assertEquals(4, revised.capacities().getFirst().freeSlots());
        assertEquals(1, StartingFleet.override(revised, "1:0").vehicles().size());
        assertThrows(IllegalArgumentException.class, () -> StartingFleet.override(source, "2:5"));
    }
}
