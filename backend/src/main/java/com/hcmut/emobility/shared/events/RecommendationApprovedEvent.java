package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M7
 * Ben nhan: M7 tao nhiem vu dieu chuyen; M5 doi lich sac neu lien quan
 */
public class RecommendationApprovedEvent extends DomainEvent {

    private final Long recommendationId;
    private final String actionType;

    public RecommendationApprovedEvent(Long recommendationId, String actionType) {
        this.recommendationId = recommendationId;
        this.actionType = actionType;
    }

    public Long getRecommendationId() { return recommendationId; }
    public String getActionType() { return actionType; }
}
