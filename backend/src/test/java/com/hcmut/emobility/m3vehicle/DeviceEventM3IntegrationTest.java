package com.hcmut.emobility.m3vehicle;

import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.domain.VehicleType;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.service.DeviceEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceEventM3IntegrationTest {

    @Autowired
    private DeviceEventService deviceEventService;

    @Autowired
    private RentalVehicleRepository vehicles;

    @Test
    void m6VehicleFailedEventReachesM3AndMovesVehicleToMaintenance() throws Exception {
        Constructor<RentalVehicle> constructor = RentalVehicle.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RentalVehicle vehicle = constructor.newInstance();
        ReflectionTestUtils.setField(vehicle, "code", "M6-TEST-VEHICLE");
        ReflectionTestUtils.setField(vehicle, "type", VehicleType.E_BIKE);
        ReflectionTestUtils.setField(vehicle, "batteryLevel", 80);
        ReflectionTestUtils.setField(vehicle, "state", VehicleState.AVAILABLE);
        ReflectionTestUtils.setField(vehicle, "currentHubId", 1L);
        vehicle = vehicles.saveAndFlush(vehicle);

        DeviceReading reading = new DeviceReading(UUID.randomUUID(), 1L, ResourceType.VEHICLE,
                vehicle.getId(), "FAULTY", 0, "MECHANICAL", OffsetDateTime.now());
        deviceEventService.processReading(reading);

        assertEquals(VehicleState.MAINTENANCE, vehicles.findById(vehicle.getId()).orElseThrow().getState());
    }
}
