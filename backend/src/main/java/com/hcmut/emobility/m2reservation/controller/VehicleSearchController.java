package com.hcmut.emobility.m2reservation.controller;

import com.hcmut.emobility.m2reservation.dto.HubSuggestion;
import com.hcmut.emobility.m2reservation.dto.VehicleDetail;
import com.hcmut.emobility.m2reservation.dto.VehicleSearchQuery;
import com.hcmut.emobility.m2reservation.dto.VehicleSummary;
import com.hcmut.emobility.m2reservation.service.VehicleSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Path convention: /api/m<number>/<resource> */
@RestController
@RequestMapping("/api/m2")
public class VehicleSearchController {

    private final VehicleSearchService service;

    public VehicleSearchController(VehicleSearchService service) {
        this.service = service;
    }

    /** FR-M2-01: GET /api/m2/vehicles/search?hubId=&type=&minBattery= */
    @GetMapping("/vehicles/search")
    public List<VehicleSummary> search(@Valid VehicleSearchQuery query) {
        return service.search(query);
    }

    /** FR-M2-02 */
    @GetMapping("/vehicles/{id}")
    public VehicleDetail detail(@PathVariable Long id) {
        return service.getDetail(id);
    }

    /** FR-M2-10: GET /api/m2/hubs/alternatives?hubId=(hub to exclude)&type=&minBattery= */
    @GetMapping("/hubs/alternatives")
    public List<HubSuggestion> suggestHubs(@Valid VehicleSearchQuery query) {
        return service.suggestHubs(query);
    }
}
