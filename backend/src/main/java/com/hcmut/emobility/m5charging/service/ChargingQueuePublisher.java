package com.hcmut.emobility.m5charging.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Best-effort STOMP notification; persistence must not depend on websocket availability. */
@Component
public class ChargingQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(ChargingQueuePublisher.class);
    private final SimpMessagingTemplate messaging;

    public ChargingQueuePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publish(Long hubId, Object payload) {
        try {
            messaging.convertAndSend("/topic/m5/hubs/" + hubId + "/queue", payload);
        } catch (Exception ex) {
            log.warn("[M5] Could not publish live queue update for hub {}: {}", hubId, ex.getMessage());
        }
    }
}
