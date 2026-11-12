package com.hcmut.emobility.m4parking.dto;

/** Tinh trang cho do cua mot Hub cho man hinh M4. capacity = hub.parking_slots (BR-M4-01). */
public record ParkingHubSummary(Long hubId, String hubName, int capacity, long free, long reserved,
                                long occupied, long blocked, long freeWithCharger) { }
