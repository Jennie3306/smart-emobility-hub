package com.hcmut.emobility.m2reservation.service;

import com.hcmut.emobility.m2reservation.dto.HubSuggestion;
import com.hcmut.emobility.m2reservation.dto.VehicleDetail;
import com.hcmut.emobility.m2reservation.dto.VehicleSearchQuery;
import com.hcmut.emobility.m2reservation.dto.VehicleSummary;
import com.hcmut.emobility.m2reservation.repository.VehicleSearchRepository;
import com.hcmut.emobility.m3vehicle.domain.RentalVehicle;
import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class VehicleSearchService {

    private static final int MAX_SUGGESTIONS = 3;   // FR-M2-10

    private final VehicleSearchRepository vehicles;

    @Value("${app.reservation.hold-minutes}")
    private int holdMinutes;

    public VehicleSearchService(VehicleSearchRepository vehicles) {
        this.vehicles = vehicles;
    }

    /** FR-M2-01: returns AVAILABLE vehicles only, highest battery first. */
    public List<VehicleSummary> search(VehicleSearchQuery query) {
        return vehicles.searchAvailable(query.hubId(), query.type(), query.minBatteryOrZero())
                .stream().map(VehicleSummary::from).toList();
    }

    /** FR-M2-02. Throws: VEHICLE_NOT_FOUND (404). */
    public VehicleDetail getDetail(Long vehicleId) {
        return VehicleDetail.from(findOrThrow(vehicleId), holdMinutes);
    }

    /** FR-M2-10: up to 3 other hubs (excluding the selected one) that still have matching vehicles. */
    public List<HubSuggestion> suggestHubs(VehicleSearchQuery query) {
        return vehicles.countAvailableByHub(query.type(), query.minBatteryOrZero()).stream()
                .filter(h -> !Objects.equals(h.getHubId(), query.hubId()))
                .limit(MAX_SUGGESTIONS)
                .map(h -> new HubSuggestion(h.getHubId(), h.getAvailableCount()))
                .toList();
    }

    /** Vehicle code shown on the reservation screen; null if not found. */
    public String findCode(Long vehicleId) {
        return vehicles.findById(vehicleId).map(RentalVehicle::getCode).orElse(null);
    }

    private RentalVehicle findOrThrow(Long id) {
        return vehicles.findById(id).orElseThrow(() ->
                new BusinessException("VEHICLE_NOT_FOUND", "Vehicle not found: " + id, HttpStatus.NOT_FOUND));
    }
}
