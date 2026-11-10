package com.hcmut.emobility.m8simulation.service;

/** Port for authoritative parking holds. No provisional implementation is registered. */
public interface DispatchParking {
    /** Hold a destination space using this attempt ID as the idempotency key. */
    void hold(String attemptId, long destinationHubId, long vehicleId);

    /** Associate the hold with the task so parking can handle completion or failure. */
    void attach(String attemptId, long taskId);

    /** Release the hold after a definitive task rejection. */
    void release(String attemptId);
}
