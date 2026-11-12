package com.hcmut.emobility.m4parking.service;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.PrivateVehicle;
import com.hcmut.emobility.m4parking.dto.BookParkingRequest;
import com.hcmut.emobility.m4parking.dto.ParkingBookingResponse;
import com.hcmut.emobility.m4parking.dto.PrivateVehicleResponse;
import com.hcmut.emobility.m4parking.dto.RegisterVehicleRequest;
import com.hcmut.emobility.m4parking.repository.ParkingBookingLogRepository;
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
 * Du lieu demo do cho (chot 12/11): Hub Ga Metro o 31/40 cho dang dung (77%, ngay duoi nguong canh bao
 * 80% cua M1) - bang luot dat THAT di qua M2 va M4, khong sua thang bang cua M1. Nho vay M1, M2, M4 va M8
 * cung mot so lieu; sinh vien demo dat them mot cho la Hub len 80% va M1 bao canh bao.
 *
 * 31 sinh vien demo (demo01..demo31, mat khau 123456): demoNN luon do o cho P-(08+NN), tuc P-09..P-39 -
 * cac cho KHONG co tru sac (P-37..P-39 la cho CAR nen ba nguoi cuoi di o to). P-01..P-08 (co tru sac) va
 * P-40 de trong cho luong demo dat cho kem sac.
 *
 * Moi lan app khoi dong, seeder "bu" lai: sinh vien demo nao khong con luot dat dang hieu luc (vi Timer da
 * tu check-out khi het khung gio, hoac da check-out tay) thi duoc dat va check-in lai mot khung 12 gio moi.
 * Vi vay luc nao mo app de demo, Hub Ga Metro cung bat dau o 77%.
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
    private final ParkingBookingLogRepository bookings;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public ParkingDemoSeeder(ParkingService parking, ParkingSpaceRepository spaces,
                             ParkingBookingLogRepository bookings, AppUserRepository users,
                             PasswordEncoder encoder, Clock clock) {
        this.parking = parking;
        this.spaces = spaces;
        this.bookings = bookings;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Tra ve so luot dat demo vua tao (0 khi ca 31 nguoi deu con luot dat dang hieu luc). */
    public int seed() {
        OffsetDateTime start = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        String passwordHash = null;
        int created = 0;
        for (int n = 1; n <= LAST_SPACE - FIRST_SPACE + 1; n++) {
            long spaceId = FIRST_SPACE + n - 1L;
            String username = String.format("demo%02d", n);
            try {
                ParkingSpace space = spaces.findById(spaceId).orElse(null);
                if (space == null || !space.getHubId().equals(DEMO_HUB)) continue;
                AppUser user = users.findByUsername(username).orElse(null);
                if (user == null) {
                    if (passwordHash == null) passwordHash = encoder.encode("123456");
                    user = users.save(new AppUser(username, passwordHash, "Sinh vien demo " + n, UserRole.STUDENT));
                }
                if (bookings.existsByStudentIdAndStatus(user.getId(), ParkingBookingLog.Status.ACTIVE)) continue;
                if (space.getStatus() != ParkingSpace.Status.FREE) continue; // cho nay dang co nguoi khac dung
                Long vehicleId = demoVehicle(user.getId(), n, space.getType());
                ParkingBookingResponse booking = parking.book(user.getId(), new BookParkingRequest(DEMO_HUB, start,
                        start.plusHours(BOOKING_HOURS), ParkingBookingLog.VehicleKind.PERSONAL, vehicleId, null,
                        space.getId(), false, null));
                parking.checkIn(user.getId(), booking.id());
                created++;
            } catch (Exception ex) {
                log.warn("[M4] Demo booking for {} at space {} skipped: {}", username, spaceId, ex.getMessage());
            }
        }
        if (created > 0) log.info("[M4] Demo data: {} checked-in bookings created at hub {}", created, DEMO_HUB);
        return created;
    }

    /** Xe dang hoat dong cua sinh vien demo; lan dau thi dang ky DEMO-NN dung loai voi cho do. */
    private Long demoVehicle(Long userId, int n, ParkingSpace.Type spaceType) {
        return parking.myVehicles(userId).stream()
                .filter(v -> v.status() == PrivateVehicle.Status.ACTIVE)
                .filter(v -> v.vehicleType().requiredSpaceType() == spaceType)
                .map(PrivateVehicleResponse::id)
                .findFirst()
                .orElseGet(() -> {
                    PrivateVehicle.VehicleType type = spaceType == ParkingSpace.Type.CAR
                            ? PrivateVehicle.VehicleType.E_CAR : PrivateVehicle.VehicleType.E_MOTORBIKE;
                    return parking.registerVehicle(userId, new RegisterVehicleRequest(String.format("DEMO-%02d", n),
                            type, type == PrivateVehicle.VehicleType.E_CAR ? "VinFast VF3" : "VinFast Evo200", 60)).id();
                });
    }
}
