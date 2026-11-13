package com.hcmut.emobility.m2reservation.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Reservation shared by M2 (rental vehicles) and M4 (parking spaces) - table reservation (V20). */
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "hub_id")
    private Long hubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationState state;

    @Column(name = "start_at")
    private OffsetDateTime startAt;      // booked time window - used by M4

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    /** "VEHICLE:12" while active, NULL once ended. The UNIQUE constraint is the last line of defence against double booking (BR-M2-01). */
    @Column(name = "active_resource_key", unique = true, length = 40)
    private String activeResourceKey;

    /** "7:VEHICLE" - at most one active vehicle reservation per student (BR-M2-03). */
    @Column(name = "active_user_key", unique = true, length = 40)
    private String activeUserKey;

    @Version
    private Long version;   // optimistic lock - Timer and check-in may run at the same moment

    protected Reservation() { }

    /** Creates a new PENDING reservation and takes the anti-double-booking keys. */
    public static Reservation create(Long userId, ResourceType type, Long resourceId, Long hubId,
                                     OffsetDateTime now) {
        Reservation r = new Reservation();
        r.userId = userId;
        r.resourceType = type;
        r.resourceId = resourceId;
        r.hubId = hubId;
        r.createdAt = now;
        r.state = ReservationState.PENDING;
        r.activeResourceKey = resourceKeyOf(type, resourceId);
        r.activeUserKey = userKeyOf(userId, type);
        return r;
    }

    /** PENDING -> CONFIRMED, holds the resource for holdMinutes minutes (BR-M2-02). */
    public void confirm(OffsetDateTime now, int holdMinutes) {
        moveTo(ReservationState.CONFIRMED, now);
        this.expiresAt = now.plusMinutes(holdMinutes);
    }

    /**
     * PENDING -> CONFIRMED for a booked time window (M4 parking). The hold lasts until holdUntil
     * (BR-M4-04: slot start + 15 minutes); after that the Timer expires it as a no-show.
     */
    public void confirmWindow(OffsetDateTime now, OffsetDateTime windowStart, OffsetDateTime windowEnd,
                              OffsetDateTime holdUntil) {
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) {
            throw new BusinessException("INVALID_TIME_SLOT", "The booking must end after it starts",
                    HttpStatus.BAD_REQUEST);
        }
        if (holdUntil == null || !holdUntil.isAfter(now)) {
            throw new BusinessException("INVALID_TIME_SLOT", "The check-in deadline has already passed",
                    HttpStatus.BAD_REQUEST);
        }
        moveTo(ReservationState.CONFIRMED, now);
        this.startAt = windowStart;
        this.endAt = windowEnd;
        this.expiresAt = holdUntil;
    }

    /** CONFIRMED -> CHECKED_IN. BR-M2-07: only valid while now < expiresAt. */
    public void checkIn(OffsetDateTime now) {
        if (state == ReservationState.CONFIRMED && isOverdue(now)) {
            throw new BusinessException("RESERVATION_EXPIRED", "Reservation expired at " + expiresAt);
        }
        moveTo(ReservationState.CHECKED_IN, now);
        this.checkedInAt = now;
    }

    public void cancel(CancelReason reason, OffsetDateTime now) {
        moveTo(ReservationState.CANCELLED, now);
        this.cancelReason = reason;
    }

    public void expire(OffsetDateTime now) {
        moveTo(ReservationState.EXPIRED, now);
    }

    public void complete(OffsetDateTime now) {
        moveTo(ReservationState.COMPLETED, now);
    }

    /** BR-M2-08: switch to a replacement resource and keep expiresAt. The state does not change. */
    public void reassignResource(Long newResourceId) {
        if (state != ReservationState.CONFIRMED) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Only CONFIRMED reservations can change resources; current state: " + state);
        }
        this.resourceId = newResourceId;
        this.activeResourceKey = resourceKeyOf(resourceType, newResourceId);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** BR-M2-07: overdue once now >= expiresAt. */
    public boolean isOverdue(OffsetDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Seconds left on the hold; 0 when no longer CONFIRMED. */
    public long remainingSeconds(OffsetDateTime now) {
        if (state != ReservationState.CONFIRMED || expiresAt == null) return 0;
        return Math.max(0, Duration.between(now, expiresAt).getSeconds());
    }

    /**
     * Every state change goes through here - there is no setState.
     * Throws: INVALID_STATE_TRANSITION when canMoveTo returns false.
     */
    private void moveTo(ReservationState target, OffsetDateTime now) {
        if (!state.canMoveTo(target)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition reservation from " + state + " to " + target);
        }
        this.state = target;
        if (target.isTerminal()) {   // ended: release the keys so the resource can be booked again
            this.activeResourceKey = null;
            this.activeUserKey = null;
            this.closedAt = now;
        }
    }

    public static String resourceKeyOf(ResourceType type, Long resourceId) {
        return type + ":" + resourceId;
    }

    public static String userKeyOf(Long userId, ResourceType type) {
        return userId + ":" + type;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ResourceType getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getHubId() { return hubId; }
    public ReservationState getState() { return state; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCheckedInAt() { return checkedInAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public CancelReason getCancelReason() { return cancelReason; }
    public String getActiveResourceKey() { return activeResourceKey; }
    public String getActiveUserKey() { return activeUserKey; }
}
