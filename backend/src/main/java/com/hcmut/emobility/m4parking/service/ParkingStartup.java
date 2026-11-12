package com.hcmut.emobility.m4parking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Khi app khoi dong xong: (1) tao du lieu demo do cho neu bat app.demo.seed-parking, (2) phat trang thai
 * moi cho do de M1 phan chieu dung so lieu cua M4 (chot 12/11: M4 la chu so huu trang thai cho do).
 */
@Component
public class ParkingStartup {

    private static final Logger log = LoggerFactory.getLogger(ParkingStartup.class);

    private final ParkingDemoSeeder seeder;
    private final ParkingService parking;
    private final boolean seedDemo;

    public ParkingStartup(ParkingDemoSeeder seeder, ParkingService parking,
                          @Value("${app.demo.seed-parking:true}") boolean seedDemo) {
        this.seeder = seeder;
        this.parking = parking;
        this.seedDemo = seedDemo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (seedDemo) {
            try {
                seeder.seed();
            } catch (Exception ex) {
                log.warn("[M4] Demo parking data not created: {}", ex.getMessage());
            }
        }
        try {
            int published = parking.publishAllSpaceStates();
            log.info("[M4] Published the state of {} parking space(s) for M1", published);
        } catch (Exception ex) {
            log.warn("[M4] Startup state sync failed: {}", ex.getMessage());
        }
    }
}
