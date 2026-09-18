package com.hcmut.emobility.m3vehicle;

import com.hcmut.emobility.m3vehicle.domain.VehicleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAU test cho moi module.
 * Ky thuat: state transition testing (Chuong 9).
 * Lien quan: FR-M3-02, BR-M3-01, TC-M3-01..03
 */
class VehicleStateTest {

    @Test
    @DisplayName("TC-M3-01: AVAILABLE -> RESERVED la chuyen hop le")
    void availableToReserved() {
        assertTrue(VehicleState.AVAILABLE.canMoveTo(VehicleState.RESERVED));
    }

    @Test
    @DisplayName("TC-M3-02: IN_USE -> RESERVED la chuyen khong hop le")
    void inUseToReservedIsInvalid() {
        assertFalse(VehicleState.IN_USE.canMoveTo(VehicleState.RESERVED));
    }

    @Test
    @DisplayName("TC-M3-03: MAINTENANCE chi ve duoc AVAILABLE")
    void maintenanceOnlyToAvailable() {
        assertTrue(VehicleState.MAINTENANCE.canMoveTo(VehicleState.AVAILABLE));
        assertFalse(VehicleState.MAINTENANCE.canMoveTo(VehicleState.IN_USE));
    }
}
