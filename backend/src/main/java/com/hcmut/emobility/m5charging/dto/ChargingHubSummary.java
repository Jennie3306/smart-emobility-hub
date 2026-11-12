package com.hcmut.emobility.m5charging.dto;

public record ChargingHubSummary(Long hubId, String hubName, long totalPoints,
                                 long availablePoints, long chargingPoints,
                                 long faultyPoints, long queuedSessions) { }
