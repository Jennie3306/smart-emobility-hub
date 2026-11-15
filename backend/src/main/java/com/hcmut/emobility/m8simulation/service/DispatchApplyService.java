package com.hcmut.emobility.m8simulation.service;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.*;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.RedistributionRecommendations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DispatchApplyService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DispatchApplyService.class);
    private final RunStore store;
    private final RedistributionRecommendations m7;

    public DispatchApplyService(RunStore store, RedistributionRecommendations m7) {
        this.store = store;
        this.m7 = m7;
    }

    public Run apply(String runId, String recommendationId, String operator) throws IOException {
        synchronized (store) {
            Run run = store.get(runId);
            if (run.snapshot().hypotheticalFleet()) {
                throw new BusinessException("HYPOTHETICAL_FLEET", "Run with a live fleet snapshot before creating dispatch tasks.");
            }
            Recommendation rec = run.recommendations().stream().filter(r -> r.id().equals(recommendationId))
                    .findFirst().orElseThrow();
            if (List.of("APPLIED", "APPROVED").contains(rec.status())) return run;
            var tasks = new ArrayList<>(rec.dispatch() == null ? List.<DispatchTask>of() : rec.dispatch().tasks());
            String m7Id = rec.dispatch() == null ? null : rec.dispatch().m7RecommendationId();
            if ("NEEDS_REVIEW".equals(rec.status())) {
                // An earlier approval was not confirmed: follow M7's own decision instead of guessing.
                var decision = m7Decision(m7Id);
                if (decision == null) return run;
                if (!"PENDING".equals(decision.status())) return reconcile(run, rec, tasks, decision);
            } else if (run.recommendations().stream().anyMatch(item -> "NEEDS_REVIEW".equals(item.status()))) {
                throw new BusinessException("DISPATCH_REVIEW_REQUIRED", "Review the unconfirmed dispatch before applying another recommendation from this run.");
            }
            if (!"dispatch".equals(rec.action()) || !List.of("PENDING", "PARTIAL", "NEEDS_REVIEW").contains(rec.status())
                    || rec.fromHubId() == rec.toHubId() || rec.amount() < 1) {
                throw new BusinessException("NOT_APPLICABLE", "Only a pending dispatch recommendation can be applied.");
            }
            final long toHubId = rec.toHubId();
            if (run.scenario() != null && run.snapshot().capacities().stream().noneMatch(c -> c.hubId() == toHubId && c.rentalFreeSlots() != null)) {
                throw new BusinessException("RENTAL_PARKING_UNKNOWN", "Capture a fresh snapshot to verify rental-bike parking before applying.");
            }
            if (m7Id == null) {
                int remaining = rec.amount() - tasks.size();
                if (remaining <= 0) return save(run, rec, "APPROVED", tasks, null, null);
                m7Id = m7.propose("M8_SIMULATION", rec.id(), rec.fromHubId(), rec.toHubId(), remaining, rec.reason());
            }
            run = save(run, rec, "NEEDS_REVIEW", tasks, m7Id,
                    "Check the M7 recommendation and its tasks before retrying an unconfirmed approval.");
            RedistributionRecommendations.ApprovalResult result;
            try {
                result = m7.approve(m7Id, operator);
            } catch (BusinessException ex) {
                if ("RECOMMENDATION_ALREADY_RESOLVED".equals(ex.getCode())) {
                    var decision = m7Decision(m7Id);
                    if (decision != null) return reconcile(run, rec, tasks, decision);
                } else if (!"RECOMMENDATION_NOT_FOUND".equals(ex.getCode())) throw ex;
                return save(run, rec, "NEEDS_REVIEW", tasks, m7Id, ex.getMessage());
            }
            for (Long taskId : result.taskIds()) {
                if (tasks.stream().noneMatch(task -> task.taskId() == taskId)) {
                    tasks.add(new DispatchTask(null, taskId));
                }
            }
            return save(run, rec, result.status(), tasks, result.recommendationId(), result.message());
        }
    }

    /**
     * Discards a proposal. When it was already registered with M7, the M7 proposal is withdrawn too, so an
     * Operator cannot approve in M7 a plan discarded here. If M7 has already approved it, M8 follows M7 instead.
     */
    public Run discard(String runId, String recommendationId, String operator) throws IOException {
        synchronized (store) {
            Run run = store.get(runId);
            Recommendation rec = run.recommendations().stream().filter(r -> r.id().equals(recommendationId))
                    .findFirst().orElseThrow();
            String m7Id = rec.dispatch() == null ? null : rec.dispatch().m7RecommendationId();
            if (!List.of("PENDING", "NEEDS_REVIEW").contains(rec.status()) || ("NEEDS_REVIEW".equals(rec.status()) && m7Id == null)) {
                throw new BusinessException("ALREADY_RESOLVED", "This proposal has already been resolved");
            }
            var tasks = new ArrayList<>(rec.dispatch() == null ? List.<DispatchTask>of() : rec.dispatch().tasks());
            if (m7Id == null) return save(run, rec, "DISCARDED", tasks, null, null);
            RedistributionRecommendations.Decision decision;
            try {
                decision = m7.withdraw(m7Id, operator, "Discarded in M8 What-if simulation");
            } catch (BusinessException ex) {
                if (!"RECOMMENDATION_NOT_FOUND".equals(ex.getCode())) throw ex;
                return save(run, rec, "DISCARDED", tasks, m7Id, "M7 recommendation #" + m7Id + " no longer exists.");
            }
            return reconcile(run, rec, tasks, decision);
        }
    }

    private RedistributionRecommendations.Decision m7Decision(String m7Id) {
        if (m7Id == null) return null;
        try {
            return m7.decision(m7Id);
        } catch (BusinessException ex) {
            if ("RECOMMENDATION_NOT_FOUND".equals(ex.getCode())) return null;
            throw ex;
        }
    }

    /**
     * Follows M7 for every proposal that M7 may still change: an Operator decision taken in M7, and task progress
     * after approval. A proposal becomes APPLIED once all of its M7 tasks have finished and at least one vehicle
     * arrived. Saved runs are rewritten only when something changed; an unreachable M7 leaves the run as it is.
     */
    public List<Run> sync(List<Run> runs) {
        synchronized (store) {
            var result = new ArrayList<Run>(runs.size());
            for (Run listed : runs) {
                Run run = listed;
                try {
                    run = syncRun(listed);
                } catch (RuntimeException | IOException ex) {
                    log.warn("[M8] Could not follow M7 for run {}: {}", listed.id(), ex.getMessage());
                }
                result.add(run);
            }
            return result;
        }
    }

    private Run syncRun(Run listed) throws IOException {
        if (listed.recommendations().stream().noneMatch(this::followsM7)) return listed;
        Run run = store.get(listed.id());
        boolean changed = false;
        var items = new ArrayList<Recommendation>();
        for (Recommendation rec : run.recommendations()) {
            Recommendation next = rec;
            if (followsM7(rec)) {
                var decision = m7Decision(rec.dispatch().m7RecommendationId());
                // A still-pending M7 proposal says nothing new; an unconfirmed approval is retried only by Apply.
                if (decision != null && !"PENDING".equals(decision.status())) next = reconciled(rec, rec.dispatch().tasks(), decision);
            }
            changed |= !next.equals(rec);
            items.add(next);
        }
        if (!changed) return run;
        Run next = run.withRecommendations(items);
        store.save(next);
        return next;
    }

    private boolean followsM7(Recommendation rec) {
        return rec.dispatch() != null && rec.dispatch().m7RecommendationId() != null
                && List.of("PENDING", "PARTIAL", "APPROVED", "NEEDS_REVIEW").contains(rec.status());
    }

    /** Mirrors M7's decision on the proposal into the saved run. */
    private Run reconcile(Run run, Recommendation rec, List<DispatchTask> known,
                          RedistributionRecommendations.Decision decision) throws IOException {
        var updated = reconciled(rec, known, decision);
        Run next = run.withRecommendations(run.recommendations().stream()
                .map(item -> item.id().equals(rec.id()) ? updated : item).toList());
        store.save(next);
        return next;
    }

    private Recommendation reconciled(Recommendation rec, List<DispatchTask> known,
                                      RedistributionRecommendations.Decision decision) {
        var tasks = new ArrayList<>(known);
        for (Long taskId : decision.taskIds()) {
            if (tasks.stream().noneMatch(task -> task.taskId() == taskId)) tasks.add(new DispatchTask(null, taskId));
        }
        String id = decision.recommendationId();
        String status;
        String message;
        switch (decision.status()) {
            case "REJECTED" -> {
                status = "DISCARDED";
                message = tasks.isEmpty() ? "M7 recommendation #" + id + " was rejected; no task was created."
                        : "M7 recommendation #" + id + " was rejected.";
            }
            case "APPROVED" -> {
                int total = decision.taskIds().size();
                if (decision.tasksFinished() && decision.doneTasks() > 0) {
                    status = "APPLIED";
                    message = decision.doneTasks() + " of " + total + " " + plural(total, "task") + " completed in M7"
                            + (decision.failedTasks() > 0 ? "; " + decision.failedTasks() + " failed." : ".");
                } else if (decision.tasksFinished()) {
                    status = "APPROVED";
                    message = "All " + total + " M7 " + plural(total, "task") + " failed; no vehicle was moved.";
                } else {
                    status = "APPROVED";
                    int finished = decision.doneTasks() + decision.failedTasks();
                    message = "Approved in M7. " + finished + " of " + total + " " + plural(total, "task") + " finished.";
                }
            }
            case "PARTIAL" -> {
                status = "PARTIAL";
                message = decision.note();
            }
            default -> {
                status = "PENDING";
                message = "Waiting for an Operator decision in M7.";
            }
        }
        return rec.withDispatch(status, new DispatchProgress(tasks, null, null, message, id));
    }

    private static String plural(int count, String word) {
        return count == 1 ? word : word + "s";
    }

    private Run save(Run run, Recommendation rec, String status, List<DispatchTask> tasks,
                     String m7Id, String message) throws IOException {
        var updated = rec.withDispatch(status, new DispatchProgress(tasks, null, null, message, m7Id));
        Run next = run.withRecommendations(run.recommendations().stream()
                .map(item -> item.id().equals(rec.id()) ? updated : item).toList());
        store.save(next);
        return next;
    }
}
