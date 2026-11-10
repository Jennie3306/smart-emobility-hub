package com.hcmut.emobility.m8simulation;

import com.hcmut.emobility.m8simulation.service.M7DispatchClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class M7DispatchClientTest {
    @Test void sendsExistingM7ContractAndRecognizesDefinitiveRejection() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new ArrayList<String>();
        server.createContext("/api/m7/redistributions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Cookie") + " " + body);
            boolean rejected = body.contains("\"vehicleId\":9");
            String response = rejected ? "{\"code\":\"DESTINATION_FULL\"}"
                    : switch (exchange.getRequestMethod()) {
                        case "GET" -> exchange.getRequestURI().getQuery().equals("status=CREATED")
                                ? "[{\"vehicleId\":12}]" : "[{\"vehicleId\":13}]";
                        default -> "{\"taskId\":45,\"vehicleId\":7}";
                    };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(rejected ? 409 : 200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new M7DispatchClient(server.getAddress().getPort());
            assertEquals(Set.of(12L, 13L), client.activeVehicles("demo"));
            assertEquals(45, client.create("demo", 7, 10, 20));
            assertEquals("DESTINATION_FULL", assertThrows(M7DispatchClient.Rejected.class,
                    () -> client.create("demo", 9, 10, 20)).getMessage());
            assertEquals(List.of(
                    "GET /api/m7/redistributions?status=CREATED JSESSIONID=demo ",
                    "GET /api/m7/redistributions?status=IN_PROGRESS JSESSIONID=demo ",
                    "POST /api/m7/redistributions JSESSIONID=demo {\"vehicleId\":7,\"sourceHubId\":10,\"destinationHubId\":20}",
                    "POST /api/m7/redistributions JSESSIONID=demo {\"vehicleId\":9,\"sourceHubId\":10,\"destinationHubId\":20}"
            ), requests);
        } finally { server.stop(0); }
    }
}
