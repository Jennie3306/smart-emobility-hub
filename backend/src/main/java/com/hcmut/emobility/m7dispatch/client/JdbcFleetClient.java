package com.hcmut.emobility.m7dispatch.client;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Cai dat tam thoi cho VehicleClient va HubClient: doc thang bang
 * rental_vehicle (M3) va hub bang SQL CHI DOC, giong cach M8 lay snapshot.
 * M7 khong bao gio ghi vao bang cua module khac; doi trang thai xe di qua
 * DispatchCompletedEvent do M3 tu xu ly.
 *
 * Khi M4 co API suc chua that, thay checkDestinationCapacity bang loi goi do
 * va ghi vao muc 4.4 Sai lech cua report.
 */
@Component
public class JdbcFleetClient implements VehicleClient, HubClient {

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

    @Override
    public Optional<ExternalDtos.HubInfo> checkDestinationCapacity(Long hubId) {
        List<Integer> capacity = jdbc.query(
                "SELECT parking_slots FROM hub WHERE id = ?",
                (rs, i) -> rs.getInt("parking_slots"),
                hubId);
        if (capacity.isEmpty()) {
            return Optional.empty();
        }
        Integer vehicles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rental_vehicle WHERE current_hub_id = ?",
                Integer.class, hubId);
        return Optional.of(new ExternalDtos.HubInfo(hubId, capacity.get(0),
                vehicles == null ? 0 : vehicles));
    }
}
