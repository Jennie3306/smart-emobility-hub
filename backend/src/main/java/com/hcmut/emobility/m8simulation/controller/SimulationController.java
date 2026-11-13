package com.hcmut.emobility.m8simulation.controller;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.m8simulation.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/m8")
public class SimulationController {
    private final SimulationService service; private final NetworkMonitor monitor;
    private final DispatchApplyService dispatch;
    public SimulationController(SimulationService service,NetworkMonitor monitor,DispatchApplyService dispatch) {
        this.service=service; this.monitor=monitor; this.dispatch=dispatch;
    }
    @GetMapping("/snapshot") public Snapshot snapshot() { return service.snapshot(); }
    @GetMapping("/status") public Map<String,Object> status() {
        var status = monitor.status(service.aiConfigured());
        status.put("applyEnabled", true);
        return status;
    }
    @PostMapping("/parse") public ParseResult parse(@Valid @RequestBody ParseRequest request) { return service.parse(request.text()); }
    @GetMapping("/runs") public List<Run> runs() { return service.list(); }
    @PostMapping("/runs") public Run run(@Valid @RequestBody Scenario scenario,@RequestParam(required=false) String snapshotRunId,@RequestParam(defaultValue="false") boolean repeatRecentBookings) throws IOException { return service.run(scenario,snapshotRunId,repeatRecentBookings); }
    @PostMapping("/monitor/check") public Map<String,Object> check() { monitor.check(); return status(); }
    @PostMapping("/runs/{id}/recommendations/{recommendationId}/discard")
    public Run discard(@PathVariable String id,@PathVariable String recommendationId) throws IOException { return service.discard(id,recommendationId); }
    @PostMapping("/runs/{id}/recommendations/{recommendationId}/apply")
    public Run apply(@PathVariable String id, @PathVariable String recommendationId, HttpServletRequest request) throws IOException {
        var session = request.getSession(false);
        return dispatch.apply(id, recommendationId, session == null ? null : session.getId());
    }
    @PostMapping("/runs/{id}/agent/retry") public Run retry(@PathVariable String id,@RequestParam int expectedRounds) throws IOException {return service.retry(id,expectedRounds);}
}
