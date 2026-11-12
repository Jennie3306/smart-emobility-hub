package com.hcmut.emobility.m4parking;

import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.domain.ParkingSpace.Status;
import com.hcmut.emobility.m4parking.domain.ParkingSpace.Type;
import com.hcmut.emobility.shared.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.BLOCKED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.FREE;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.OCCUPIED;
import static com.hcmut.emobility.m4parking.domain.ParkingSpace.Status.RESERVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Muc domain cua M4: vong doi trang thai cua ParkingSpace (muc 4.1 cua report).
 * Ky thuat: state transition testing.
 * Lien quan: BR-M4-05, BR-M4-06, BR-M4-07; TC-M4-08..13, TC-M4-16..21; loi D-M4-01..03.
 */
class ParkingSpaceStateTest {

    private static final String INVALID_TRANSITION = "INVALID_STATE_TRANSITION";
    private static final String SPACE_STILL_IN_USE = "SPACE_STILL_IN_USE";
    private static final String INVALID_RESERVATION_ID = "INVALID_RESERVATION_ID";
    private static final String RESERVATION_MISMATCH = "RESERVATION_MISMATCH";

    @Test
    @DisplayName("TC-M4-08: Vong doi hop le FREE -> RESERVED -> OCCUPIED -> FREE")
    void validLifecycle() {
        ParkingSpace space = newSpace();
        assertState(space, FREE, null);

        space.book(1001L);
        assertState(space, RESERVED, 1001L);

        space.checkIn(1001L);
        assertState(space, OCCUPIED, 1001L);

        space.release(1001L);
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
    @DisplayName("TC-M4-09 (S04): FREE + checkIn(id) bi chan, khong dat truoc thi khong duoc chiem cho (BR-M4-05)")
    void checkInOnFreeSpaceIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_TRANSITION, () -> space.checkIn(1001L));

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-09 (S05): BLOCKED + book(id) bi chan, cho gap su co khong duoc dat (BR-M4-06)")
    void bookOnBlockedSpaceIsRejected() {
        ParkingSpace space = newSpace();
        space.block();

        assertRejected(INVALID_TRANSITION, () -> space.book(2002L));

        assertState(space, BLOCKED, null);
    }

    @Test
    @DisplayName("TC-M4-09 (S06): BLOCKED + checkIn(id) bi chan, cho bi khoa sau khi da duoc dat (BR-M4-06)")
    void checkInOnBlockedSpaceIsRejected() {
        ParkingSpace space = reservedSpace(5005L);
        space.block();

        assertRejected(INVALID_TRANSITION, () -> space.checkIn(5005L));

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
    @DisplayName("TC-M4-10 (muc domain, S09): release(id) tren cho FREE bi chan, handler phai kiem tra trang thai truoc khi goi")
    void releaseOnFreeSpaceIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_TRANSITION, () -> space.release(5005L));

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-11, TC-M4-12a (S10, S11): block() giu reservation de xu ly sau (BR-M4-06)")
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
    @DisplayName("TC-M4-12b (S13): release(id) tren cho BLOCKED bi chan, check-out khong tra cho hong ve FREE (D-M4-02)")
    void releaseOnBlockedSpaceIsRejected() {
        ParkingSpace space = occupiedSpace(7007L);
        space.block();

        assertRejected(INVALID_TRANSITION, () -> space.release(7007L));

        assertState(space, BLOCKED, 7007L);
    }

    @Test
    @DisplayName("TC-M4-12c: check-out tren cho BLOCKED dung detachReservation(id), cho van BLOCKED va het gan reservation")
    void detachReservationKeepsSpaceBlocked() {
        ParkingSpace space = occupiedSpace(7007L);
        space.block();

        space.detachReservation(7007L);

        assertState(space, BLOCKED, null);
    }

    @Test
    @DisplayName("detachReservation(id) chi chay tren cho BLOCKED, trang thai khac bi chan va giu nguyen reservation")
    void detachReservationOnNonBlockedSpaceIsRejected() {
        ParkingSpace free = newSpace();
        ParkingSpace reserved = reservedSpace(9009L);
        ParkingSpace occupied = occupiedSpace(9010L);

        assertRejected(INVALID_TRANSITION, () -> free.detachReservation(9008L));
        assertRejected(INVALID_TRANSITION, () -> reserved.detachReservation(9009L));
        assertRejected(INVALID_TRANSITION, () -> occupied.detachReservation(9010L));

        assertState(free, FREE, null);
        assertState(reserved, RESERVED, 9009L);
        assertState(occupied, OCCUPIED, 9010L);
    }

    @Test
    @DisplayName("TC-M4-13 (S12): unblock() khi cho con gan reservation bi tu choi, khong de lai cho FREE mang reservation cu (D-M4-01)")
    void unblockWithAttachedReservationIsRejected() {
        ParkingSpace space = reservedSpace(8008L);
        space.block();

        assertRejected(SPACE_STILL_IN_USE, space::unblock);

        assertState(space, BLOCKED, 8008L);
    }

    @Test
    @DisplayName("TC-M4-13: sau detachReservation(id), unblock() tra cho ve FREE va khong con reservation")
    void unblockAfterDetachFreesSpace() {
        ParkingSpace space = reservedSpace(8008L);
        space.block();
        space.detachReservation(8008L);

        space.unblock();

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("unblock() tren cho khong BLOCKED bi chan, chi cho BLOCKED moi duoc mo khoa")
    void unblockOnFreeSpaceIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_TRANSITION, space::unblock);

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-16 (S14): khong con setStatus(), setCurrentReservationId() hay changeStatus() public de bo qua state machine (D-M4-03)")
    void noPublicSetterCanBypassStateMachine() {
        Set<String> publicMethods = Arrays.stream(ParkingSpace.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(publicMethods.contains("setStatus"), "setStatus() public di vong qua canMoveTo");
        assertFalse(publicMethods.contains("setCurrentReservationId"),
                "setCurrentReservationId() public di vong qua canMoveTo");
        assertFalse(publicMethods.contains("changeStatus"),
                "changeStatus() public cho phep doi status ma khong xoa currentReservationId");
    }

    @Test
    @DisplayName("TC-M4-17: book(null) bi tu choi, cho van giu FREE va khong gan reservation")
    void bookWithNullReservationIdIsRejected() {
        ParkingSpace space = newSpace();

        assertRejected(INVALID_RESERVATION_ID, HttpStatus.BAD_REQUEST, () -> space.book(null));

        assertState(space, FREE, null);
    }

    @Test
    @DisplayName("TC-M4-18: checkIn(id) tu choi null hoac ma reservation khong khop")
    void checkInWithMissingOrMismatchedReservationIsRejected() {
        ParkingSpace space = reservedSpace(1001L);

        assertRejected(INVALID_RESERVATION_ID, HttpStatus.BAD_REQUEST, () -> space.checkIn(null));
        assertRejected(RESERVATION_MISMATCH, () -> space.checkIn(9999L));

        assertState(space, RESERVED, 1001L);
    }

    @Test
    @DisplayName("TC-M4-19: release(id) tu choi null hoac su kien tre cua reservation A tren cho da dat cho B")
    void releaseWithMissingOrMismatchedReservationIsRejected() {
        ParkingSpace reservedByB = reservedSpace(2002L);
        assertRejected(INVALID_RESERVATION_ID, HttpStatus.BAD_REQUEST, () -> reservedByB.release(null));
        assertRejected(RESERVATION_MISMATCH, () -> reservedByB.release(1001L));
        assertState(reservedByB, RESERVED, 2002L);

        ParkingSpace occupiedByC = occupiedSpace(3003L);
        assertRejected(INVALID_RESERVATION_ID, HttpStatus.BAD_REQUEST, () -> occupiedByC.release(null));
        assertRejected(RESERVATION_MISMATCH, () -> occupiedByC.release(1001L));
        assertState(occupiedByC, OCCUPIED, 3003L);
    }

    @Test
    @DisplayName("TC-M4-20: khong con setHubId() / setType() public lam lech dinh danh hoac loai cho")
    void noPublicSetterCanMutateHubOrType() {
        Set<String> publicMethods = Arrays.stream(ParkingSpace.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(publicMethods.contains("setHubId"), "setHubId() public co the lam lech id khop voi M1");
        assertFalse(publicMethods.contains("setType"), "setType() public cho phep doi loai cho tuy tien");
    }

    @Test
    @DisplayName("TC-M4-21: detachReservation(id) tren cho BLOCKED tu choi null hoac su kien tre cua reservation khac")
    void detachReservationWithMissingOrMismatchedReservationIsRejected() {
        ParkingSpace space = reservedSpace(2002L);
        space.block();

        assertRejected(INVALID_RESERVATION_ID, HttpStatus.BAD_REQUEST, () -> space.detachReservation(null));
        assertRejected(RESERVATION_MISMATCH, () -> space.detachReservation(1001L));

        assertState(space, BLOCKED, 2002L);
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
        space.checkIn(reservationId);
        return space;
    }

    private static void assertRejected(String expectedCode, Executable action) {
        assertRejected(expectedCode, HttpStatus.CONFLICT, action);
    }

    private static void assertRejected(String expectedCode, HttpStatus expectedStatus, Executable action) {
        BusinessException error = assertThrows(BusinessException.class, action);
        assertEquals(expectedCode, error.getCode());
        assertEquals(expectedStatus, error.getStatus());
    }

    private static void assertState(ParkingSpace space, Status status, Long reservationId) {
        assertEquals(status, space.getStatus(), "status");
        assertEquals(reservationId, space.getCurrentReservationId(), "currentReservationId");
    }
}
