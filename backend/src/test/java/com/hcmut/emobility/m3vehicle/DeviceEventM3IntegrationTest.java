package com.hcmut.emobility.m3vehicle;

import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m6simulator.domain.DeviceReading;
import com.hcmut.emobility.m6simulator.domain.ProcessingResult;
import com.hcmut.emobility.m6simulator.domain.ResourceType;
import com.hcmut.emobility.m6simulator.service.DeviceEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * DemoDeviceCatalog cua M6 chi biet VEHICLE id 1, nen xe test phai co dung id 1.
     * Chen bang SQL voi id co dinh: neu de JPA tu sinh id thi ket qua phu thuoc thu tu
     * chay test (test khac trong cung context da dung het id 1 cua H2 du da rollback).
     */
    @Test
    void m6VehicleFailedEventReachesM3AndMovesVehicleToMaintenance() {
        long vehicleId = 1L;
        jdbc.update("insert into rental_vehicle(id,code,type,battery_level,state,current_hub_id,version) "
                + "values(?,'M6-TEST-VEHICLE','E_BIKE',80,'AVAILABLE',1,0)", vehicleId);

        DeviceReading reading = new DeviceReading(UUID.randomUUID(), 1L, ResourceType.VEHICLE,
                vehicleId, "FAULTY", 0, "MECHANICAL", OffsetDateTime.now());
        ProcessingResult result = deviceEventService.processReading(reading);

        assertEquals(ProcessingResult.Status.PROCESSED, result.status(),
                "M6 tu choi reading - kiem tra DemoDeviceCatalog va id xe test");
        assertEquals(VehicleState.MAINTENANCE, vehicles.findById(vehicleId).orElseThrow().getState());
    }
}
