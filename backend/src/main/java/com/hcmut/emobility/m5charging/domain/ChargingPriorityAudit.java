package com.hcmut.emobility.m5charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "charging_priority_audit")
public class ChargingPriorityAudit {

    /** V53: what the Operator changed. A schedule move keeps the priority, so old and new are equal. */
    public enum ChangeType { PRIORITY_CHANGE, SCHEDULE_MOVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(name = "old_priority", nullable = false, length = 20)
    private String oldPriority;

    @Column(name = "new_priority", nullable = false, length = 20)
    private String newPriority;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType = ChangeType.PRIORITY_CHANGE;

    protected ChargingPriorityAudit() { }

    public ChargingPriorityAudit(Long sessionId, Long operatorUserId, String oldPriority,
                                 String newPriority, String reason, OffsetDateTime changedAt) {
        this.sessionId = sessionId;
        this.operatorUserId = operatorUserId;
        this.oldPriority = oldPriority;
        this.newPriority = newPriority;
        this.reason = reason;
        this.changedAt = changedAt;
        this.changeType = ChangeType.PRIORITY_CHANGE;
    }

    public ChargingPriorityAudit(Long sessionId, Long operatorUserId, String oldPriority,
                                 String newPriority, String reason, OffsetDateTime changedAt,
                                 ChangeType changeType) {
        this(sessionId, operatorUserId, oldPriority, newPriority, reason, changedAt);
        this.changeType = changeType == null ? ChangeType.PRIORITY_CHANGE : changeType;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Long getOperatorUserId() { return operatorUserId; }
    public String getOldPriority() { return oldPriority; }
    public String getNewPriority() { return newPriority; }
    public String getReason() { return reason; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public ChangeType getChangeType() { return changeType; }
}
