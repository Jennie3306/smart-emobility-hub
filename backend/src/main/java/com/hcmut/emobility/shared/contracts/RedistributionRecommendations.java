package com.hcmut.emobility.shared.contracts;

import java.util.List;

/**
 * Redistribution proposals waiting for an Operator's decision (FR-M7-02). Implemented by M7
 * (RecommendationService); M8 calls it instead of M7's REST API (decision 3 of the module review:
 * M8 generates, M7 approves and executes).
 *
 * A proposal says "move N vehicles from hub A to hub B". M7 chooses the vehicles when the proposal is approved,
 * because the fleet may have changed since the simulation ran, and it checks BR-M7-01..03 for every vehicle.
 */
public interface RedistributionRecommendations {

    /**
     * Registers a proposal for review in M7's screen. Idempotent: the same (source, externalRef) returns the
     * proposal registered before.
     *
     * @param source      who proposes, e.g. "M8_SIMULATION"
     * @param externalRef the proposer's own id (M8: recommendation id of the simulation run)
     * @return M7's recommendation id
     * @throws com.hcmut.emobility.shared.common.BusinessException VALIDATION_FAILED (400), HUB_NOT_FOUND (404)
     */
    String propose(String source, String externalRef, long fromHubId, long toHubId, int amount, String reason);

    /**
     * Approves a proposal on behalf of an Operator and creates the redistribution tasks it allows.
     * RECOMMENDATION_NOT_FOUND (404), RECOMMENDATION_ALREADY_RESOLVED (409).
     */
    ApprovalResult approve(String recommendationId, String approvedBy);

    /**
     * @param status  APPROVED (all vehicles got a task), PARTIAL (some did), or PENDING (none could be created yet)
     * @param taskIds tasks created by this call
     * @param message why fewer tasks than requested were created, or null
     */
    record ApprovalResult(String recommendationId, String status, List<Long> taskIds, String message) { }
}
