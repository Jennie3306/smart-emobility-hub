package com.hcmut.emobility.m8simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class M7DispatchClient {
    private static final Set<String> REJECTIONS = Set.of("VALIDATION_FAILED", "SAME_HUB", "VEHICLE_NOT_FOUND",
            "VEHICLE_RESERVED", "VEHICLE_NOT_AT_SOURCE", "TASK_ALREADY_ACTIVE", "HUB_NOT_FOUND", "DESTINATION_FULL");
    private final RestClient client;

    public M7DispatchClient(@Value("${local.server.port:${server.port:8080}}") int port) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(6));
        client = RestClient.builder().requestFactory(factory)
                .baseUrl("http://127.0.0.1:" + port + "/api/m7/redistributions").build();
    }

    public Set<Long> activeVehicles(String sessionId) {
        var ids = new HashSet<Long>();
        for (String status : List.of("CREATED", "IN_PROGRESS")) {
            JsonNode response = client.get().uri("?status={status}", status)
                    .header(HttpHeaders.COOKIE, cookie(sessionId)).retrieve().body(JsonNode.class);
            if (response == null || !response.isArray()) throw new IllegalStateException("Invalid M7 task list");
            for (JsonNode task : response) {
                long id = task.path("vehicleId").asLong();
                if (id <= 0) throw new IllegalStateException("Invalid M7 vehicle ID");
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    public long create(String sessionId, long vehicleId, long fromHubId, long toHubId) {
        try {
            JsonNode task = client.post().header(HttpHeaders.COOKIE, cookie(sessionId))
                    .body(new CreateTask(vehicleId, fromHubId, toHubId)).retrieve().body(JsonNode.class);
            if (task == null || task.path("taskId").asLong() <= 0 || task.path("vehicleId").asLong() != vehicleId)
                throw new IllegalStateException("Invalid M7 task response");
            return task.path("taskId").asLong();
        } catch (HttpClientErrorException e) {
            JsonNode error = e.getResponseBodyAs(JsonNode.class);
            String code = error == null ? "" : error.path("code").asText();
            if (REJECTIONS.contains(code)) throw new Rejected(code);
            throw e;
        }
    }

    private String cookie(String sessionId) {
        return "JSESSIONID=" + sessionId;
    }

    private record CreateTask(long vehicleId, long sourceHubId, long destinationHubId) {}

    public static class Rejected extends RuntimeException {
        public Rejected(String code) { super(code); }
    }
}
