package com.hcmut.emobility.shared.events;

/**
 * Phat boi: M7 (sau khi Operator duyet khuyen nghi cua M8)
 * Ben nhan: M5 doi lich sac neu lien quan
 *
 * recommendationId la String: M8 dinh danh khuyen nghi bang UUID dang chuoi.
 * (Doi tu Long khi tich hop; chua co module nao nhan su kien nay.)
 */
public class RecommendationApprovedEvent extends DomainEvent {

    private final String recommendationId;
    private final String actionType;

    public RecommendationApprovedEvent(String recommendationId, String actionType) {
        this.recommendationId = recommendationId;
        this.actionType = actionType;
    }

    public String getRecommendationId() { return recommendationId; }
    public String getActionType() { return actionType; }
}
