package com.hcmut.emobility.m5charging.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FR-M5-04/09/10: non-interactive queue refresh, charging progress and 10-minute hold expiry. */
@Component
public class ChargingScheduler {

    private final ChargingService charging;

    public ChargingScheduler(ChargingService charging) {
        this.charging = charging;
    }

    @Scheduled(fixedDelayString = "${app.charging.recalculation-interval-ms:60000}")
    public void run() {
        charging.runScheduledTasks();
    }
}
