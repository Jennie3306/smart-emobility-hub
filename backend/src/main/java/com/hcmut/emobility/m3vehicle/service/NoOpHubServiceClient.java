package com.hcmut.emobility.m3vehicle.service;

import org.springframework.stereotype.Component;

@Component
public class NoOpHubServiceClient implements HubServiceClient {

    @Override
    public boolean hasAvailableSpace(Long hubId) {
        return true; // stub — thay khi M4 sẵn sàng
    }
}