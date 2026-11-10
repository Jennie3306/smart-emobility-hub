package com.hcmut.emobility.m1monitoring.domain;

import com.hcmut.emobility.shared.common.BusinessException;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Thuc the duy nhat module M1 so huu. M1 tao, luu va chuyen trang thai no.
 * Trang thai tai nguyen (xe, cho do, tru sac) thuoc M6; M1 chi doc.
 */
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertState state;

    /** Chi so gay ra canh bao: % su dung, hoac so xe con lai. */
    @Column(name = "trigger_value", nullable = false)
    private int triggerValue;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "acknowledged_by", length = 50)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "dismiss_reason", length = 200)
    private String dismissReason;

    protected Alert() { }

    public static Alert raise(Long hubId, AlertType type, int triggerValue) {
        Alert a = new Alert();
        a.hubId = hubId;
        a.type = type;
        a.state = AlertState.OPEN;
        a.triggerValue = triggerValue;
        a.raisedAt = OffsetDateTime.now();
        return a;
    }

    /**
     * FR-M1-11. Tien dieu kien: canh bao dang o OPEN.
     * Hau dieu kien: state = ACKNOWLEDGED, ghi lai nguoi va thoi diem.
     * Ngoai le: INVALID_STATE_TRANSITION neu canh bao da dong.
     */
    public void acknowledge(String username) {
        moveTo(AlertState.ACKNOWLEDGED);
        this.acknowledgedBy = username;
        this.acknowledgedAt = OffsetDateTime.now();
    }

    /** FR-M1-11. Bo qua canh bao, bat buoc co ly do. */
    public void dismiss(String reason) {
        moveTo(AlertState.DISMISSED);
        this.dismissReason = reason;
        this.closedAt = OffsetDateTime.now();
    }

    /** FR-M1-09. He thong tu dong dong khi dieu kien da het (BR-M1-07). */
    public void resolve() {
        moveTo(AlertState.RESOLVED);
        this.closedAt = OffsetDateTime.now();
    }

    /** BR-M1-06: dieu kien van con vuot nguong thi chi cap nhat so lieu. */
    public void refresh(int latestValue) {
        this.triggerValue = latestValue;
    }

    private void moveTo(AlertState target) {
        if (!state.canMoveTo(target)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot move alert from " + state + " to " + target);
        }
        this.state = target;
    }

    public Long getId() { return id; }
    public Long getHubId() { return hubId; }
    public AlertType getType() { return type; }
    public AlertState getState() { return state; }
    public int getTriggerValue() { return triggerValue; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public String getDismissReason() { return dismissReason; }
}
