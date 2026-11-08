package com.hcmut.emobility.m7dispatch.client;

public interface RecommendationClient { 
    ExternalDtos.Recommendation getRecommendation(String recommendationId); 
}