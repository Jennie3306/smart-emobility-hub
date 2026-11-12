package com.hcmut.emobility.m4parking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-M4-04, actor Timer. Luong chinh: Timer cua M2 danh dau reservation EXPIRED va M4 nhan
 * ReservationExpiredEvent. Job nay la luoi an toan (UC-M4-04 4a, thu lai): luot dat qua han ma M2 da dong
 * reservation nhung M4 chua giai phong cho (vi du su kien bi bo lo khi loi) thi giai phong o day.
 * Moi luot dat chay trong giao dich rieng qua proxy cua ParkingService.
 */
@Component
public class ParkingNoShowJob {

    private static final Logger log = LoggerFactory.getLogger(ParkingNoShowJob.class);

    private final ParkingService parking;

    public ParkingNoShowJob(ParkingService parking) {
        this.parking = parking;
    }

    @Scheduled(initialDelayString = "${app.parking.no-show-scan-ms:60000}",
               fixedDelayString = "${app.parking.no-show-scan-ms:60000}")
    public void run() {
        int released = 0;
        for (Long reservationId : parking.overdueReservationIds()) {
            try {
                if (parking.releaseIfReservationEnded(reservationId)) released++;
            } catch (Exception ex) {
                log.warn("[M4] Could not release overdue reservation {}: {}", reservationId, ex.getMessage());
            }
        }
        if (released > 0) log.info("[M4] Timer: released {} no-show parking space(s)", released);
    }
}
