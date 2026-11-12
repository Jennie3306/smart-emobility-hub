package com.hcmut.emobility.m7dispatch.client;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * VehicleClient: doc thang bang rental_vehicle (M3) bang SQL CHI DOC, giong cach M8 lay snapshot.
 * M7 khong bao gio ghi vao bang cua module khac; doi trang thai xe di qua DispatchStarted / DispatchCompleted /
 * DispatchFailed do M3 tu xu ly. Suc chua Hub dich lay tu M4 qua ParkingHubClient (Appendix A v2).
 */
@Component
public class JdbcFleetClient implements VehicleClient {

    private final JdbcTemplate jdbc;

    public JdbcFleetClient(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ExternalDtos.VehicleInfo> checkVehicle(Long vehicleId) {
        List<ExternalDtos.VehicleInfo> rows = jdbc.query(
                "SELECT id, state, current_hub_id FROM rental_vehicle WHERE id = ?",
                (rs, i) -> new ExternalDtos.VehicleInfo(
                        rs.getLong("id"),
                        rs.getString("state"),
                        rs.getObject("current_hub_id", Long.class)),
                vehicleId);
        return rows.stream().findFirst();
    }
}
