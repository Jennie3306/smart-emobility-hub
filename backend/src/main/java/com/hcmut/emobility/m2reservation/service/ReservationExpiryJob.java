package com.hcmut.emobility.m2reservation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR-M2-05, actor Timer: scans for overdue reservations.
 * Each reservation expires in its own transaction (called through the ReservationService proxy),
 * so one failing row does not roll back the others.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService service;

    public ReservationExpiryJob(ReservationService service) {
        this.service = service;
    }

    /**
     * Every 30 seconds by default; override with --app.reservation.expiry-scan-ms, no application.yml change needed.
     * The first scan also waits one period, so it does not run while the context is starting (or during tests).
     */
    @Scheduled(initialDelayString = "${app.reservation.expiry-scan-ms:30000}",
               fixedDelayString = "${app.reservation.expiry-scan-ms:30000}")
    public void run() {
        int expired = 0;
        for (Long id : service.findOverdueIds()) {
            try {
                if (service.expire(id)) expired++;
            } catch (Exception ex) {
                log.warn("[M2] Could not expire reservation {}: {}", id, ex.getMessage());
            }
        }
        if (expired > 0) log.info("[M2] Timer: {} reservation(s) expired", expired);
    }
}
