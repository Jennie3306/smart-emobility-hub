package com.hcmut.emobility.m8simulation.controller;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/m8")
public class SimulationController {
    private final SimulationService service; private final NetworkMonitor monitor;
    public SimulationController(SimulationService service,NetworkMonitor monitor) { this.service=service;this.monitor=monitor; }
    @GetMapping("/snapshot") public Snapshot snapshot() { return service.snapshot(); }
    @GetMapping("/status") public Map<String,Object> status() { return monitor.status(service.aiConfigured()); }
    @PostMapping("/parse") public ParseResult parse(@Valid @RequestBody ParseRequest request) { return service.parse(request.text()); }
    @GetMapping("/runs") public List<Run> runs() { return service.list(); }
    @PostMapping("/runs") public Run run(@Valid @RequestBody Scenario scenario,@RequestParam(required=false) String snapshotRunId) throws IOException { return service.run(scenario,snapshotRunId); }
    @PostMapping("/runs/{id}/recommendations/{recommendationId}/discard")
    public Run discard(@PathVariable String id,@PathVariable String recommendationId) throws IOException { return service.discard(id,recommendationId); }
    @PostMapping("/runs/{id}/recommendations/{recommendationId}/apply")
    public void apply(@PathVariable String id,@PathVariable String recommendationId) { service.apply(id,recommendationId); }
}
