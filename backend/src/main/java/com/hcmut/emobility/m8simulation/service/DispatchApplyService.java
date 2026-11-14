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
    private final RunStore store;
    private final RedistributionRecommendations m7;

    public DispatchApplyService(RunStore store, RedistributionRecommendations m7) {
        this.store = store;
        this.m7 = m7;
    }

    public Run apply(String runId, String recommendationId, String operator) throws IOException {
        synchronized (store) {
            Run run = store.get(runId);
            Recommendation rec = run.recommendations().stream().filter(r -> r.id().equals(recommendationId))
                    .findFirst().orElseThrow();
            if (List.of("APPLIED", "APPROVED", "NEEDS_REVIEW").contains(rec.status())) return run;
            if (run.recommendations().stream().anyMatch(item -> "NEEDS_REVIEW".equals(item.status()))) {
                throw new BusinessException("DISPATCH_REVIEW_REQUIRED", "Review the unconfirmed dispatch before applying another recommendation from this run.");
            }
            if (!"dispatch".equals(rec.action()) || !List.of("PENDING", "PARTIAL").contains(rec.status())
                    || rec.fromHubId() == rec.toHubId() || rec.amount() < 1) {
                throw new BusinessException("NOT_APPLICABLE", "Only a pending dispatch recommendation can be applied.");
            }
            var tasks = new ArrayList<>(rec.dispatch() == null ? List.<DispatchTask>of() : rec.dispatch().tasks());
            String m7Id = rec.dispatch() == null ? null : rec.dispatch().m7RecommendationId();
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
                if (!"RECOMMENDATION_ALREADY_RESOLVED".equals(ex.getCode())
                        && !"RECOMMENDATION_NOT_FOUND".equals(ex.getCode())) throw ex;
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

    private Run save(Run run, Recommendation rec, String status, List<DispatchTask> tasks,
                     String m7Id, String message) throws IOException {
        var updated = rec.withDispatch(status, new DispatchProgress(tasks, null, null, message, m7Id));
        Run next = run.withRecommendations(run.recommendations().stream()
                .map(item -> item.id().equals(rec.id()) ? updated : item).toList());
        store.save(next);
        return next;
    }
}
