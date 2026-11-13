package com.hcmut.emobility.m7dispatch.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * FR-M7-02, FR-M7-04. "Move {amount} vehicles from the source hub to the destination hub", proposed by M7's own
 * rule when M1 raises an alert (source M1_ALERT) or by the What-if module (M8_SIMULATION). The Operator approves
 * or rejects it; approval creates one RedistributionTask per vehicle.
 */
@Entity
@Table(name = "redistribution_recommendation")
public class RedistributionRecommendation {

    public static final String SOURCE_M1_ALERT = "M1_ALERT";
    public static final String SOURCE_M8_SIMULATION = "M8_SIMULATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String source;

    /** The proposer's own id (M1 alert event id, M8 recommendation id); makes propose() idempotent. */
    @Column(name = "external_ref", length = 64)
    private String externalRef;

    @Column(name = "source_hub_id", nullable = false)
    private Long sourceHubId;

    @Column(name = "destination_hub_id", nullable = false)
    private Long destinationHubId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "task_count", nullable = false)
    private int taskCount;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    /** Result of the last decision: why fewer tasks were created, or the rejection reason. */
    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    public static RedistributionRecommendation pending(String source, String externalRef, Long sourceHubId,
                                                       Long destinationHubId, int amount, String reason,
                                                       LocalDateTime now) {
        RedistributionRecommendation r = new RedistributionRecommendation();
        r.source = source;
        r.externalRef = externalRef;
        r.sourceHubId = sourceHubId;
        r.destinationHubId = destinationHubId;
        r.amount = amount;
        r.reason = reason;
        r.status = RecommendationStatus.PENDING;
        r.createdAt = now;
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSource() { return source; }
    public String getExternalRef() { return externalRef; }
    public Long getSourceHubId() { return sourceHubId; }
    public Long getDestinationHubId() { return destinationHubId; }
    public int getAmount() { return amount; }
    public int getTaskCount() { return taskCount; }
    public void setTaskCount(int taskCount) { this.taskCount = taskCount; }
    public String getReason() { return reason; }
    public RecommendationStatus getStatus() { return status; }
    public void setStatus(RecommendationStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
