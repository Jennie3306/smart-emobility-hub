package com.hcmut.emobility.m2reservation.domain;

/** Why a reservation moved to CANCELLED (cancellation reason table, section 4.1). */
public enum CancelReason { USER_REQUEST, VEHICLE_FAILED, RESOURCE_BLOCKED, REJECTED_CONFLICT }
