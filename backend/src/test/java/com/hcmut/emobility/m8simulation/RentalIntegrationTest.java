package com.hcmut.emobility.m8simulation;

import com.hcmut.emobility.m2reservation.service.ReservationService;
import com.hcmut.emobility.m2reservation.repository.ReservationRepository;
import com.hcmut.emobility.m2reservation.domain.ReservationState;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.m3vehicle.repository.*;
import com.hcmut.emobility.m3vehicle.domain.*;
import com.hcmut.emobility.shared.events.*;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class RentalIntegrationTest {
    @Autowired ReservationService bookings;
    @Autowired ReservationRepository reservations;
    @Autowired VehicleService rentals;
    @Autowired RentalVehicleRepository vehicles;
    @Autowired RentalSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    private long vehicleId;
    @BeforeEach void setup() {
        jdbc.update("insert into hub(id,name,location) values(9000,'Integration Hub','Test')");
        // BR-M4-08: tra xe thue can mot cho trong quy cho do chung cua Hub
        jdbc.update("insert into parking_space(hub_id,space_code,type,status,version) values(9000,'P-01','BIKE','FREE',0)");
        String code="I-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("insert into rental_vehicle(code,type,battery_level,state,current_hub_id,version) values(?,'E_BIKE',90,'AVAILABLE',9000,0)",code);
        vehicleId=jdbc.queryForObject("select id from rental_vehicle where code=?",Long.class,code);
    }
    @Test void bookingPickupReturnKeepsBothModulesConsistent() {
        var r=bookings.reserveVehicle(9001L,vehicleId);
        assertEquals(VehicleState.RESERVED,vehicles.findById(vehicleId).orElseThrow().getState());
        assertEquals(9001L,vehicles.findById(vehicleId).orElseThrow().getReservedByUserId());
        bookings.checkIn(r.getId(),9001L);
        assertEquals(VehicleState.IN_USE,vehicles.findById(vehicleId).orElseThrow().getState());
        assertNull(vehicles.findById(vehicleId).orElseThrow().getCurrentHubId());
        assertTrue(sessions.findByVehicleIdAndStatus(vehicleId,SessionStatus.ACTIVE).isPresent());
        rentals.returnVehicle(vehicleId,9000L,75,9001L);
        assertEquals(ReservationState.COMPLETED,reservations.findById(r.getId()).orElseThrow().getState());
        assertEquals(VehicleState.AVAILABLE,vehicles.findById(vehicleId).orElseThrow().getState());
        assertTrue(sessions.findByVehicleIdAndStatus(vehicleId,SessionStatus.ACTIVE).isEmpty());
    }
    @Test void cancellationReleasesTheVehicle() {
        var r=bookings.reserveVehicle(9001L,vehicleId);
        bookings.cancel(r.getId(),9001L);
        assertEquals(VehicleState.AVAILABLE,vehicles.findById(vehicleId).orElseThrow().getState());
        assertNull(vehicles.findById(vehicleId).orElseThrow().getReservedByUserId());
    }
    @Test void expiryReleasesTheVehicle() {
        var r=bookings.reserveVehicle(9001L,vehicleId);
        jdbc.update("update reservation set expires_at=? where id=?",OffsetDateTime.now().minusMinutes(1),r.getId());
        em.clear();assertTrue(bookings.expire(r.getId()));
        assertEquals(VehicleState.AVAILABLE,vehicles.findById(vehicleId).orElseThrow().getState());
    }
    @Test void wrongOwnerCannotCheckIn() {
        var r=bookings.reserveVehicle(9001L,vehicleId);
        assertThrows(BusinessException.class,()->bookings.checkIn(r.getId(),9002L));
        assertEquals(VehicleState.RESERVED,vehicles.findById(vehicleId).orElseThrow().getState());
        assertTrue(sessions.findByVehicleIdAndStatus(vehicleId,SessionStatus.ACTIVE).isEmpty());
    }
    @Test void legacyDirectPickupAlsoUpdatesM2() {
        var r=bookings.reserveVehicle(9001L,vehicleId);rentals.pickUp(vehicleId,9001L);
        assertEquals(ReservationState.CHECKED_IN,reservations.findById(r.getId()).orElseThrow().getState());
    }
    @Test void consumersDoNotSuppressEachOther() {
        var store=new ProcessedEventStore();var id=UUID.randomUUID();
        assertTrue(store.markIfNew("M1",id));assertTrue(store.markIfNew("M3",id));assertFalse(store.markIfNew("M1",id));
    }
}
