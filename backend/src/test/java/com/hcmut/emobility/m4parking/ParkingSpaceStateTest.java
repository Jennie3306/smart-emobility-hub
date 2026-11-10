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

    @Test
    @DisplayName("TC-M4-09 (S06): BLOCKED + checkIn() bi chan, cho bi khoa sau khi da duoc dat (BR-M4-06)")
    void checkInOnBlockedSpaceIsRejected() {
        ParkingSpace space = reservedSpace(5005L);
        space.block();

        assertRejected(INVALID_TRANSITION, space::checkIn);

        assertState(space, BLOCKED, 5005L);
    }

    @Test
    @DisplayName("TC-M4-09 (S07): OCCUPIED + book() bi chan, khong dat chong len cho dang co xe (BR-M4-07)")
    void bookOnOccupiedSpaceIsRejected() {
        ParkingSpace space = occupiedSpace(3003L);

        assertRejected(INVALID_TRANSITION, () -> space.book(3004L));

        assertState(space, OCCUPIED, 3003L);
    }

    @Test
    @DisplayName("TC-M4-09 (S08): RESERVED + book() bi chan, khong dat chong len cho da co chu (BR-M4-07)")
    void bookOnReservedSpaceIsRejected() {
        ParkingSpace space = reservedSpace(4004L);

        assertRejected(INVALID_TRANSITION, () -> space.book(4005L));

        assertState(space, RESERVED, 4004L);
    }

    @Test
    @DisplayName("TC-M4-10 (muc domain, S09): release() tren cho FREE bi chan, handler phai kiem tra trang thai truoc khi goi")
    void releaseOnFreeSpaceIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_TRANSITION, space::release);

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-11, TC-M4-12 (S10, S11): block() giu reservation de xu ly sau (BR-M4-06)")
    void blockKeepsReservation() {
        ParkingSpace free = newSpace();
        free.block();
        assertState(free, BLOCKED, null);

        ParkingSpace reserved = reservedSpace(6006L);
        reserved.block();
        assertState(reserved, BLOCKED, 6006L);

        ParkingSpace occupied = occupiedSpace(7007L);
        occupied.block();
        assertState(occupied, BLOCKED, 7007L);
    }

    @Test
    @DisplayName("TC-M4-12 (S13): release() tren cho BLOCKED bi chan, check-out khong tra cho hong ve FREE (D-M4-02)")
    void releaseOnBlockedSpaceIsRejected() {
        ParkingSpace space = occupiedSpace(7007L);
        space.block();

        assertRejected(INVALID_TRANSITION, space::release);

        assertState(space, BLOCKED, 7007L);
    }

    @Test
    @DisplayName("TC-M4-12: check-out tren cho BLOCKED dung detachReservation(), cho van BLOCKED va het gan reservation")
    void detachReservationKeepsSpaceBlocked() {
        ParkingSpace space = occupiedSpace(7007L);
        space.block();

        space.detachReservation();

        assertState(space, BLOCKED, null);
    }

    @Test
    @DisplayName("detachReservation() chi chay tren cho BLOCKED, trang thai khac bi chan va giu nguyen reservation")
    void detachReservationOnNonBlockedSpaceIsRejected() {
        ParkingSpace free = newSpace();
        ParkingSpace reserved = reservedSpace(9009L);
        ParkingSpace occupied = occupiedSpace(9010L);

        assertRejected(INVALID_TRANSITION, free::detachReservation);
        assertRejected(INVALID_TRANSITION, reserved::detachReservation);
        assertRejected(INVALID_TRANSITION, occupied::detachReservation);

        assertState(free, FREE, null);
        assertState(reserved, RESERVED, 9009L);
        assertState(occupied, OCCUPIED, 9010L);
    }

    private static ParkingSpace newSpace() {
        return new ParkingSpace(1L, "P-01", Type.BIKE);
    }

    private static ParkingSpace reservedSpace(long reservationId) {
        ParkingSpace space = newSpace();
        space.book(reservationId);
        return space;
    }

    private static ParkingSpace occupiedSpace(long reservationId) {
        ParkingSpace space = reservedSpace(reservationId);
        space.checkIn();
        return space;
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
