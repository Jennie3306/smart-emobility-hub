package com.hcmut.emobility.m7dispatch.client;

import java.util.Optional;

/** M7 lay mot khuyen nghi dieu chuyen tu M8. */
public interface RecommendationClient {
    Optional<ExternalDtos.Recommendation> getRecommendation(String recommendationId);
}
