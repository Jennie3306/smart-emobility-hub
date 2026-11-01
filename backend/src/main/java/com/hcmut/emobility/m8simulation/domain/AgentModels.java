package com.hcmut.emobility.m8simulation.domain;

import com.hcmut.emobility.m8simulation.domain.SimulationModels.Kpi;
import java.util.List;

public final class AgentModels {
    private AgentModels() {}
    public record PlanAction(String action,long fromHubId,long toHubId,int amount,String reason) {}
    public record PlanEvaluation(boolean valid,List<PlanAction> actions,Kpi kpi,String feedback) {}
    public record AgentRound(int number,String tool,String callId,String arguments,boolean valid,
                             List<PlanAction> actions,Kpi kpi,String feedback) {}
    public record AgentState(int totalRounds,String status,String message,List<AgentRound> rounds,PlanEvaluation best) {
        public AgentState {rounds=List.copyOf(rounds);}
    }
}
