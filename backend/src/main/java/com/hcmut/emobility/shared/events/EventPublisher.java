package com.hcmut.emobility.shared.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Cong phat su kien duy nhat cua he thong.
 * Module phat su kien -> KHONG duoc goi truc tiep service cua module khac.
 *
 *   publisher.publish(new ChargerFailedEvent(pointId, hubId, "OFFLINE"));
 */
@Component
public class EventPublisher {

    private final ApplicationEventPublisher delegate;

    public EventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
