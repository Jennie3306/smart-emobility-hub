package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.shared.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DispatchApplyService {
    private final SnapshotProvider snapshots;
    private final RunStore store;
    private final M7DispatchClient m7;

    public DispatchApplyService(SnapshotProvider snapshots, RunStore store, M7DispatchClient m7) {
        this.snapshots = snapshots;
        this.store = store;
        this.m7 = m7;
    }

    public Run apply(String runId, String recommendationId, String sessionId) throws IOException {
        // RunStore is local and single-process. Serialize approvals and other updates to the saved run.
        synchronized (store) {
            return applyLocked(runId, recommendationId, sessionId);
        }
    }

    private Run applyLocked(String runId, String recommendationId, String sessionId) throws IOException {
        Run run = store.get(runId);
        Recommendation rec = run.recommendations().stream().filter(r -> r.id().equals(recommendationId))
                .findFirst().orElseThrow();
        if ("APPLIED".equals(rec.status()) || "NEEDS_REVIEW".equals(rec.status())) return run;
        if (run.recommendations().stream().anyMatch(item -> "NEEDS_REVIEW".equals(item.status()))) {
            throw new BusinessException("DISPATCH_REVIEW_REQUIRED", "Review the unconfirmed dispatch before applying another recommendation from this run.");
        }
        if (!"dispatch".equals(rec.action()) || !List.of("PENDING", "PARTIAL").contains(rec.status())
                || rec.fromHubId() == rec.toHubId() || rec.amount() < 1) {
            throw new BusinessException("NOT_APPLICABLE", "Only a pending dispatch recommendation can be applied.");
        }
        if (sessionId == null) throw new BusinessException("SESSION_REQUIRED", "Sign in again to apply this recommendation.", HttpStatus.UNAUTHORIZED);

        var tasks = new ArrayList<>(rec.dispatch() == null ? List.<DispatchTask>of() : rec.dispatch().tasks());
        var active = m7.activeVehicles(sessionId);
        var live = snapshots.capture();
        if (live.hubs().stream().noneMatch(hub -> hub.id() == rec.toHubId()))
            throw new BusinessException("DESTINATION_UNAVAILABLE", "The destination hub is no longer available.");
        var vehicles = live.vehicles().stream()
                .filter(v -> Objects.equals(v.hubId(), rec.fromHubId()) && "AVAILABLE".equals(v.state()) && v.battery() >= 20)
                .filter(v -> !active.contains(v.id()) && tasks.stream().noneMatch(task -> task.vehicleId() == v.id()))
                .sorted(Comparator.comparingLong(Vehicle::id)).toList();
        int limit = Math.min(rec.amount() - tasks.size(), Math.max(0, vehicles.size() - 1));
        for (Vehicle vehicle : vehicles.subList(0, limit)) {
            String attemptId = UUID.randomUUID().toString();
            run = save(run, rec, "NEEDS_REVIEW", tasks, vehicle.id(), attemptId,
                    "Task outcome is unconfirmed. Check the M7 task list before creating another task.");
            long taskId;
            try {
                taskId = m7.create(sessionId, vehicle.id(), rec.fromHubId(), rec.toHubId());
            } catch (M7DispatchClient.Rejected rejected) {
                return finish(run, rec, tasks, "M7 declined vehicle #" + vehicle.id() + " (" + rejected.getMessage() + ").");
            } catch (RuntimeException uncertain) {
                return run;
            }
            tasks.add(new DispatchTask(vehicle.id(), taskId));
            run = finish(run, rec, tasks, null);
        }
        if (tasks.size() < rec.amount()) {
            return finish(run, rec, tasks, "Too few eligible vehicles at the source. One eligible vehicle must remain there.");
        }
        return run;
    }

    private Run finish(Run run, Recommendation rec, List<DispatchTask> tasks, String message) throws IOException {
        String status = tasks.size() == rec.amount() ? "APPLIED" : tasks.isEmpty() ? "PENDING" : "PARTIAL";
        return save(run, rec, status, tasks, null, null, message);
    }

    private Run save(Run run, Recommendation rec, String status, List<DispatchTask> tasks,
                     Long vehicleId, String attemptId, String message) throws IOException {
        var updated = rec.withDispatch(status, new DispatchProgress(tasks, vehicleId, attemptId, message));
        Run next = run.withRecommendations(run.recommendations().stream()
                .map(item -> item.id().equals(rec.id()) ? updated : item).toList());
        store.save(next);
        return next;
    }
}
