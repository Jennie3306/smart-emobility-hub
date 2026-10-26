package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

/** Database reads share REPEATABLE_READ; device overlays are copied separately and timestamped. */
@Service
public class JdbcSnapshotProvider implements SnapshotProvider {
    private final JdbcTemplate jdbc;
    private final Map<String, DeviceState> devices = new LinkedHashMap<>();
    public JdbcSnapshotProvider(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @EventListener
    public synchronized void onResourceState(ResourceStateUpdatedEvent event) {
        String key = event.getResourceType() + ":" + event.getResourceId();
        devices.put(key, new DeviceState(event.getHubId(), event.getResourceType(), event.getResourceId(),
                event.getNewState(), Instant.now()));
        if (devices.size() > 10000) devices.remove(devices.keySet().iterator().next());
    }
    private synchronized List<DeviceState> deviceCopy() { return List.copyOf(devices.values()); }
    @Override
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Snapshot capture() {
        Instant capturedAt=Instant.now();
        var hubs = jdbc.query("select id,name,parking_slots,charging_points from hub order by id",
            (rs,n) -> new Hub(rs.getLong(1),displayName(rs.getString(2)),rs.getInt(3),rs.getInt(4)));
        var vehicles = jdbc.query("select id,current_hub_id,battery_level,state from rental_vehicle order by id",
            (rs,n) -> new Vehicle(rs.getLong(1),rs.getObject(2,Long.class),rs.getInt(3),rs.getString(4)));
        Instant from=capturedAt.minusSeconds(1800);
        var demand=jdbc.query("""
            select h.id,
              (select count(*) from reservation r where r.hub_id=h.id and r.resource_type='VEHICLE' and r.created_at>=? and r.created_at<=?),
              (select count(*) from reservation r where r.hub_id=h.id and r.resource_type='VEHICLE' and r.state='CONFIRMED' and r.expires_at>?)
            from hub h order by h.id
            """,(rs,n)->new HubDemand(rs.getLong(1),rs.getInt(2),rs.getInt(3)),java.sql.Timestamp.from(from),java.sql.Timestamp.from(capturedAt),java.sql.Timestamp.from(capturedAt));
        var capacities=jdbc.query("""
            select hub_id,
              sum(case when resource_type='PARKING_SPACE' then 1 else 0 end),
              sum(case when resource_type='PARKING_SPACE' and state in ('OCCUPIED','RESERVED') then 1 else 0 end),
              sum(case when resource_type='PARKING_SPACE' and state='FREE' then 1 else 0 end),
              sum(case when resource_type='CHARGING_POINT' and state not in ('FAULTY','UNDER_REPAIR') then 1 else 0 end),
              sum(case when resource_type='CHARGING_POINT' and state='IN_USE' then 1 else 0 end)
            from m1_resource_state group by hub_id order by hub_id
            """,(rs,n)->new CapacityState(rs.getLong(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6)));
        return new Snapshot(UUID.randomUUID().toString(),capturedAt,hubs,vehicles,deviceCopy(),
                "MANUAL_SCENARIO",new BookingWindow(from,capturedAt,30,demand),capacities);
    }
    private String displayName(String name) {
        return switch(name) {
            case "Hub Ga Metro" -> "Metro Hub";
            case "Hub KTX Khu A" -> "Dormitory A Hub";
            case "Hub KTX Khu B" -> "Dormitory B Hub";
            case "Hub BK" -> "Bach Khoa Hub";
            case "Hub KHTN" -> "Science Hub";
            default -> name;
        };
    }
}
