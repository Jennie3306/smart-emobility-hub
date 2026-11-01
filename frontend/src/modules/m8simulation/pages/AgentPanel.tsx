import type { Run, PlanAction } from "../api/simulationApi";

export function AgentPanel({run,busy,configured,onRetry}:{run:Run;busy:boolean;configured:boolean;onRetry:()=>void}) {
  const state=run.agent;
  const hub=(id:number)=>run.snapshot.hubs.find(h=>h.id===id)?.name??`Hub ${id}`;
  const action=(a:PlanAction)=>a.action==="reroute"?"Allow requests to use other hubs":`Move ${a.amount} vehicles: ${hub(a.fromHubId)} → ${hub(a.toHubId)}`;
  return <section className="m8-panel m8-agent" aria-label="AI planning session">
    <div className="m8-section-head"><div><h2>AI planning · test, read, refine</h2><p>Up to 3 AI rounds per attempt. Every tool tests a complete plan on the saved snapshot.</p></div><span className="m8-chip">{state?`${state.totalRounds} rounds used`:"Not started"}</span></div>
    <p className="m8-footnote">Saved run: {run.scenario.name} · {run.kpi.requests} requests · seed {run.scenario.seed}. Retry continues this saved run; edited form values are not used.</p>
    {state&&<p role="status" className={state.status==="PROVIDER_ERROR"||state.status==="INVALID_RESPONSE"?"m8-alert error":"m8-alert"}>{state.message}</p>}
    {busy&&<p role="status">AI is testing plans and reading simulation feedback. This can take up to two minutes.</p>}
    {state?.best?.kpi?<div className="m8-agent-best"><h3>Best verified simulation plan</h3><p>{run.kpi.failed} → {state.best.kpi.failed} failed requests · {state.best.kpi.served}/{state.best.kpi.requests} served · mean wait {state.best.kpi.meanWaitMins.toFixed(1)} min</p><ol>{state.best.actions.map((a,i)=><li key={i}>{action(a)}<p className="m8-footnote">{a.reason}</p></li>)}</ol><p className="m8-footnote">{state.best.feedback}</p></div>:<p className="m8-muted">{state?"No tool-tested plan has improved the saved scenario yet.":"Start planning to let AI call the simulation tool and revise its plan from real KPI feedback."}</p>}
    {state?.rounds.length?<details open><summary>Planning history · {state.totalRounds} rounds{state.totalRounds>state.rounds.length?" (latest 60 shown)":""}</summary><ol className="m8-agent-rounds">{state.rounds.map(r=><li key={r.number}><strong>Round {r.number} · {r.tool||"No tool executed"}</strong><p>{r.valid?`Simulated: ${r.kpi?.served}/${r.kpi?.requests} served · ${r.kpi?.failed} failed · mean wait ${r.kpi?.meanWaitMins.toFixed(1)} min`:"No valid simulation result"}</p>{r.actions.map((a,i)=><p key={i}>{action(a)} — {a.reason}</p>)}<p className="m8-footnote">{r.feedback}</p></li>)}</ol></details>:null}
    <button className="m8-button primary" disabled={busy||!configured} onClick={onRetry}>{busy?"Planning…":state?"Retry · +3 rounds":"Start AI planning · 3 rounds"}</button>
    {!configured&&<p className="m8-footnote">AI provider is not configured.</p>}
    <p className="m8-integration-note">Simulation only. No live action is executed. Parking uses provisional M1 observations; M4/M7 execution remains pending. AI may stop early when no useful new test remains.</p>
  </section>;
}
