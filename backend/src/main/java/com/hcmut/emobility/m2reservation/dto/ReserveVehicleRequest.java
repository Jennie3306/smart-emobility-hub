package com.hcmut.emobility.m2reservation.dto;

import jakarta.validation.constraints.NotNull;

/** Only vehicleId is accepted - userId comes from the login session, never from the body. */
public record ReserveVehicleRequest(@NotNull Long vehicleId) { }
