package com.hcmut.emobility.m7dispatch.client;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * M8 hien chua co API tra mot khuyen nghi theo id cho M7: khuyen nghi cua M8
 * la "chuyen N xe tu hub A sang hub B" (khong chi dinh xe), con M7 can mot xe
 * cu the. TV7 va TV8 can chot cach chon xe truoc khi noi hai module.
 * Cho den luc do, duyet khuyen nghi tra ve RECOMMENDATION_NOT_FOUND thay vi
 * tao nhiem vu tu du lieu bia. Ghi vao muc 4.4 Sai lech cua M7 va M8.
 */
@Component
public class NotConnectedRecommendationClient implements RecommendationClient {

    @Override
    public Optional<ExternalDtos.Recommendation> getRecommendation(String recommendationId) {
        return Optional.empty();
    }
}
