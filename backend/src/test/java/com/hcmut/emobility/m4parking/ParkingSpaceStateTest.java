package com.hcmut.emobility.m4parking;

import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.ParkingSpace.Status;
import com.hcmut.emobility.m4parking.domain.ParkingSpace.Type;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.BLOCKED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.FREE;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.OCCUPIED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.RESERVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Muc domain cua M4: vong doi trang thai cua ParkingSpace (muc 4.1 cua report).
 * Ky thuat: state transition testing.
 * Lien quan: BR-M4-05, BR-M4-06, BR-M4-07.
 */
class ParkingSpaceStateTest {

    private static final String INVALID_TRANSITION = "INVALID_STATE_TRANSITION";

    @Test
    @DisplayName("TC-M4-08: Vong doi hop le FREE -> RESERVED -> OCCUPIED -> FREE")
    void validLifecycle() {
        ParkingSpace space = newSpace();
        assertState(space, FREE, null);

        space.book(1001L);
        assertState(space, RESERVED, 1001L);

        space.checkIn();
        assertState(space, OCCUPIED, 1001L);

        space.release();
        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("Muc 4.1: Status.canMoveTo khop bang chuyen trang thai (16 cap)")
    void transitionTableMatchesDesign() {
        Map<Status, Set<Status>> allowed = Map.of(
                FREE, EnumSet.of(RESERVED, BLOCKED),
                RESERVED, EnumSet.of(OCCUPIED, FREE, BLOCKED),
                OCCUPIED, EnumSet.of(FREE, BLOCKED),
                BLOCKED, EnumSet.of(FREE));

        for (Status from : Status.values()) {
            for (Status to : Status.values()) {
                assertEquals(allowed.get(from).contains(to), from.canMoveTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    @DisplayName("TC-M4-09 (S04): FREE + checkIn() bi chan, khong dat truoc thi khong duoc chiem cho (BR-M4-05)")
    void checkInOnFreeSpaceIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_TRANSITION, space::checkIn);

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-09 (S05): BLOCKED + book() bi chan, cho gap su co khong duoc dat (BR-M4-06)")
    void bookOnBlockedSpaceIsRejected() {
        ParkingSpace space = newSpace();
        space.block();

        assertRejected(INVALID_TRANSITION, () -> space.book(2002L));

        assertState(space, BLOCKED, null);
    }

    private static ParkingSpace newSpace() {
        return new ParkingSpace(1L, "P-01", Type.BIKE);
    }

    private static void assertRejected(String expectedCode, Executable action) {
        BusinessException error = assertThrows(BusinessException.class, action);
        assertEquals(expectedCode, error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    private static void assertState(ParkingSpace space, Status status, Long reservationId) {
        assertEquals(status, space.getStatus(), "status");
        assertEquals(reservationId, space.getCurrentReservationId(), "currentReservationId");
    }
}
