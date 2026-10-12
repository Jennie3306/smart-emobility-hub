package com.hcmut.emobility.m3vehicle.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rental_session")
public class RentalSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    protected RentalSession() { }

    public static RentalSession start(Long vehicleId, Long userId) {
        RentalSession session = new RentalSession();
        session.vehicleId = vehicleId;
        session.userId = userId;
        session.startTime = LocalDateTime.now();
        session.status = SessionStatus.ACTIVE;
        return session;
    }

    public void complete() {
        this.endTime = LocalDateTime.now();
        this.status = SessionStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public Long getVehicleId() { return vehicleId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public SessionStatus getStatus() { return status; }
}