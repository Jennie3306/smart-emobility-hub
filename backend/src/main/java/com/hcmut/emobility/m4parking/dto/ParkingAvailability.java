package com.hcmut.emobility.m4parking.dto;

import java.time.OffsetDateTime;

/**
 * BR-M4-01: suc chua con lai cua Hub trong khung gio [startAt, endAt).
 * BR-M4-08: dockedRentalVehicles = xe thue dang dung o Hub, chiem cho trong moi khung gio;
 * remaining = capacity - activeBookings - dockedRentalVehicles.
 */
public record ParkingAvailability(Long hubId, OffsetDateTime startAt, OffsetDateTime endAt, int capacity,
                                  long activeBookings, long dockedRentalVehicles, long remaining, long freeSpaces) { }
