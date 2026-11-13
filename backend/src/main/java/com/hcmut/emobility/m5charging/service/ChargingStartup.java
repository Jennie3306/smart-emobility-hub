package com.hcmut.emobility.m5charging.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Chot 12/11: M5 la chu so huu trang thai tru sac. Khi app khoi dong xong, phat trang thai moi tru mot lan
 * de M1 (va M8) phan chieu dung so lieu M5 dang giu, ke ca tren database da seed truoc khi co M5.
 */
@Component
public class ChargingStartup {

    private static final Logger log = LoggerFactory.getLogger(ChargingStartup.class);

    private final ChargingService charging;

    public ChargingStartup(ChargingService charging) {
        this.charging = charging;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int published = charging.publishAllPointStates();
            log.info("[M5] Published the state of {} charging point(s) for M1", published);
        } catch (Exception ex) {
            log.warn("[M5] Startup state sync failed: {}", ex.getMessage());
        }
    }
}
