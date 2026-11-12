package com.hcmut.emobility.m4parking.dto;

import java.time.OffsetDateTime;

/** BR-M4-01: suc chua con lai cua Hub trong khung gio [startAt, endAt). */
public record ParkingAvailability(Long hubId, OffsetDateTime startAt, OffsetDateTime endAt, int capacity,
                                  long activeBookings, long remaining, long freeSpaces) { }
