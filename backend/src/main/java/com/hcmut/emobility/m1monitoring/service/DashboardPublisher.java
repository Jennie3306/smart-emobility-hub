package com.hcmut.emobility.m1monitoring.service;

import com.hcmut.emobility.m1monitoring.dto.Dtos.AlertDto;
import com.hcmut.emobility.m1monitoring.dto.Dtos.HubStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Boc kenh STOMP lai mot cho, de service khong phai biet duong truyen.
 * Dashboard dang ky /topic/hubs va /topic/alerts.
 */
@Component
public class DashboardPublisher {

    private static final Logger log = LoggerFactory.getLogger(DashboardPublisher.class);

    private final SimpMessagingTemplate messaging;

    public DashboardPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publishHubStatus(HubStatusDto dto) {
        send("/topic/hubs", dto);
    }

    public void publishAlert(AlertDto dto) {
        send("/topic/alerts", dto);
    }

    private void send(String destination, Object payload) {
        try {
            messaging.convertAndSend(destination, payload);
        } catch (Exception ex) {
            // Day that bai khong duoc lam hong giao dich dang chay:
            // du lieu da luu, lan danh gia sau se day lai.
            log.warn("[M1] Khong day duoc ban tin toi {}: {}", destination, ex.getMessage());
        }
    }
}
