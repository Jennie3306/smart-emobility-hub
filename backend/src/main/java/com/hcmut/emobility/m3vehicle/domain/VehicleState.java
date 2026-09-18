package com.hcmut.emobility.m3vehicle.domain;

/** State machine cua RentalVehicle - phai khop so do trong report (muc 3.2). */
public enum VehicleState {
    AVAILABLE,
    RESERVED,
    IN_USE,
    LOW_BATTERY,
    CHARGING,
    MAINTENANCE,
    IN_TRANSIT;

    /** Cac chuyen trang thai hop le. Chuyen khong co trong bang nay bi chan (BR-M3-01). */
    public boolean canMoveTo(VehicleState target) {
        return switch (this) {
            case AVAILABLE   -> target == RESERVED || target == MAINTENANCE || target == IN_TRANSIT || target == LOW_BATTERY;
            case RESERVED    -> target == IN_USE || target == AVAILABLE || target == MAINTENANCE;
            case IN_USE      -> target == AVAILABLE || target == LOW_BATTERY || target == MAINTENANCE;
            case LOW_BATTERY -> target == CHARGING || target == MAINTENANCE;
            case CHARGING    -> target == AVAILABLE || target == MAINTENANCE;
            case MAINTENANCE -> target == AVAILABLE;
            case IN_TRANSIT  -> target == AVAILABLE;
        };
    }
}
