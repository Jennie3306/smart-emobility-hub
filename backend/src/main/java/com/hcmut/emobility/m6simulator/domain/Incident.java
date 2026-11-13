package com.hcmut.emobility.m6simulator.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * FR-M6-01/02/05. A reported fault of one vehicle or one charging point and the work to fix it.
 * Owned by M6. The lifecycle graph is IncidentStatus (BR-M6-04); this entity adds who and when.
 * An Incident never changes the vehicle or charging point itself: M6 announces the failure and the owner
 * (M3 vehicle, M5 charging point, M4 the attached parking space) applies its own state (BR-M6-08).
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentSeverity severity;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentSource source;

    @Column(name = "reported_by", nullable = false, length = 50)
    private String reportedBy;

    @Column(length = 50)
    private String assignee;

    /** Last note written with a transition (repair done, recurrence...). */
    @Column(length = 500)
    private String note;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    /** FR-M6-02: the version the Operator has read must match, otherwise the update is refused. */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Incident() { }

    public static Incident report(ResourceType resourceType, Long resourceId, Long hubId, String reason,
                                  IncidentSeverity severity, String description, IncidentSource source,
                                  String reportedBy, OffsetDateTime now) {
        Incident i = new Incident();
        i.resourceType = resourceType;
        i.resourceId = resourceId;
        i.hubId = hubId;
        i.reason = reason;
        i.severity = severity;
        i.description = description;
        i.source = source;
        i.reportedBy = reportedBy;
        i.status = IncidentLifecycle.reported().status();
        i.reportedAt = now;
        return i;
    }

    /**
     * Applies one action of BR-M6-04. The graph check is IncidentStatus.transition, which throws
     * INVALID_STATE_TRANSITION and leaves this Incident unchanged.
     */
    public void apply(IncidentAction action, String assigneeUsername, String note, OffsetDateTime now) {
        IncidentStatus target = status.transition(action);
        switch (action) {
            case ASSIGN -> { this.assignee = assigneeUsername; this.assignedAt = now; }
            case START -> this.startedAt = now;
            case RESOLVE -> this.resolvedAt = now;
            case REOPEN -> this.resolvedAt = null;      // back to work; startedAt keeps the first start
            case CLOSE -> this.closedAt = now;
        }
        if (note != null && !note.isBlank()) this.note = note.trim();
        this.status = target;
    }

    public boolean isActive() {
        return status == IncidentStatus.REPORTED || status == IncidentStatus.ASSIGNED
                || status == IncidentStatus.IN_PROGRESS;
    }

    public Long getId() { return id; }
    public ResourceType getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getHubId() { return hubId; }
    public String getReason() { return reason; }
    public IncidentSeverity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public IncidentStatus getStatus() { return status; }
    public IncidentSource getSource() { return source; }
    public String getReportedBy() { return reportedBy; }
    public String getAssignee() { return assignee; }
    public String getNote() { return note; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public Long getVersion() { return version; }
}
