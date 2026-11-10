package com.hcmut.emobility.m7dispatch.events;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RedistributionEventHandler {

    @EventListener
    public void handleHubNearCapacity(Events.HubNearCapacityEvent event) {
        System.out.println("M7 nhận cảnh báo từ M1: Hub " + event.getHubId() + " đang thiếu/dư xe!");
        // Println cho dễ debug ở console, chuẩn phong cách sinh viên
    }
}