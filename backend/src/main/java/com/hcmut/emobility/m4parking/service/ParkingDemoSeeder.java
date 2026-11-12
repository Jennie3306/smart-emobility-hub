package com.hcmut.emobility.m4parking.service;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.repository.ParkingSpaceRepository;
import com.hcmut.emobility.shared.auth.AppUser;
import com.hcmut.emobility.shared.auth.AppUserRepository;
import com.hcmut.emobility.shared.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Du lieu demo do cho (chot 12/11): Hub Ga Metro bat dau o 31/40 cho dang dung (77%, ngay duoi nguong
 * canh bao 80% cua M1) - nhung bang luot dat THAT di qua M2 va M4, khong sua thang bang cua M1.
 * Nho vay M1, M2, M4 va M8 cung mot so lieu; sinh vien demo dat them mot cho la Hub len 80% va M1 bao canh bao.
 *
 * 31 sinh vien demo (demo01..demo31, mat khau 123456), moi nguoi mot xe va mot luot da check-in, dung cac
 * cho KHONG co tru sac (P-09..P-36 cho BIKE, P-37..P-39 cho CAR). P-01..P-08 (co tru sac) va P-40 de trong
 * cho luong demo dat cho kem sac. Chi chay mot lan: neu da co tai khoan demo01 thi bo qua.
 */
@Component
public class ParkingDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(ParkingDemoSeeder.class);

    static final long DEMO_HUB = 1L;
    /** V40: cho P-nn cua Hub 1 co id = nn. */
    static final int FIRST_SPACE = 9;
    static final int LAST_SPACE = 39;
    static final int BOOKING_HOURS = 12;

    private final ParkingService parking;
    private final ParkingSpaceRepository spaces;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public ParkingDemoSeeder(ParkingService parking, ParkingSpaceRepository spaces, AppUserRepository users,
                             PasswordEncoder encoder, Clock clock) {
        this.parking = parking;
        this.spaces = spaces;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Tra ve so luot dat demo da tao (0 neu da seed truoc do hoac khong co Hub 1). */
    public int seed() {
        if (users.findByUsername("demo01").isPresent()) return 0;
        OffsetDateTime start = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        String password = encoder.encode("123456");
        int created = 0;
        for (int spaceId = FIRST_SPACE; spaceId <= LAST_SPACE; spaceId++) {
            ParkingSpace space = spaces.findById((long) spaceId).orElse(null);
            if (space == null || !space.getHubId().equals(DEMO_HUB) || space.getStatus() != ParkingSpace.Status.FREE) {
                continue;
            }
            int n = spaceId - FIRST_SPACE + 1;
            try {
                AppUser user = users.save(new AppUser(String.format("demo%02d", n), password,
                        "Sinh vien demo " + n, UserRole.STUDENT));
                PrivateVehicle.VehicleType type = space.getType() == ParkingSpace.Type.CAR
                        ? PrivateVehicle.VehicleType.E_CAR : PrivateVehicle.VehicleType.E_MOTORBIKE;
                Long vehicleId = parking.registerVehicle(user.getId(), new RegisterVehicleRequest(
                        String.format("DEMO-%02d", n), type, type == PrivateVehicle.VehicleType.E_CAR ? "VinFast VF3" : "VinFast Evo200",
                        60)).id();
                ParkingBookingResponse booking = parking.book(user.getId(), new BookParkingRequest(DEMO_HUB, start,
                        start.plusHours(BOOKING_HOURS), ParkingBookingLog.VehicleKind.PERSONAL, vehicleId, null,
                        space.getId(), false, null));
                parking.checkIn(user.getId(), booking.id());
                created++;
            } catch (Exception ex) {
                log.warn("[M4] Demo booking for space {} skipped: {}", spaceId, ex.getMessage());
            }
        }
        log.info("[M4] Demo data: {} checked-in bookings created at hub {}", created, DEMO_HUB);
        return created;
    }
}
