package com.hcmut.emobility.m7dispatch.repository;

import com.hcmut.emobility.m7dispatch.domain.RecommendationStatus;
import com.hcmut.emobility.m7dispatch.domain.RedistributionRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RedistributionRecommendationRepository extends JpaRepository<RedistributionRecommendation, Long> {

    Optional<RedistributionRecommendation> findBySourceAndExternalRef(String source, String externalRef);

    boolean existsByDestinationHubIdAndStatusIn(Long destinationHubId, Collection<RecommendationStatus> statuses);

    boolean existsBySourceHubIdAndStatusIn(Long sourceHubId, Collection<RecommendationStatus> statuses);

    List<RedistributionRecommendation> findAllByOrderByCreatedAtDescIdDesc();

    List<RedistributionRecommendation> findByStatusOrderByCreatedAtDescIdDesc(RecommendationStatus status);
}
