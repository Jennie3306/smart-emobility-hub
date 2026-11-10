import type { Run, PlanAction } from "../api/simulationApi";

const count = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 0 });
const wait = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 1 });

export function AgentPanel({ run, busy, configured, onRetry }: {
  run: Run; busy: boolean; configured: boolean; onRetry: () => void;
}) {
  const state = run.agent;
  const best = state?.best;
  const hub = (id: number) => run.snapshot.hubs.find(h => h.id === id)?.name ?? `Hub ${id}`;
  const action = (a: PlanAction) => a.action === "reroute"
    ? "Serve rental requests at other open hubs"
    : `Move ${count.format(a.amount)} vehicles from ${hub(a.fromHubId)} to ${hub(a.toHubId)}`;
  const failed = state?.status === "PROVIDER_ERROR" || state?.status === "INVALID_RESPONSE";
  const dispatchStarted = run.recommendations.some(item => ["PARTIAL", "APPLIED", "NEEDS_REVIEW"].includes(item.status));

  return <section className="m8-panel m8-agent" aria-label="AI planning session" aria-busy={busy}>
    <div className="m8-section-head">
      <span className="m8-step" aria-hidden="true">3</span>
      <div><h2>AI Plan Exploration</h2><p>AI proposes a plan. The simulator tests it and sends the results back.</p></div>
      <span className="m8-chip">{state ? `${state.totalRounds} total rounds` : "Not started"}</span>
    </div>
    {state && <p role="status" className={failed ? "m8-alert error" : "m8-agent-status"}>{state.message}</p>}
    {busy && <p role="status" className="m8-agent-status">Testing plans and reading feedback… Each attempt uses up to 3 rounds.</p>}
    {best?.kpi ? <div className="m8-agent-best">
      <div className="m8-outcome-title"><h3>Best Tested Plan</h3><span className="m8-chip success">{count.format(run.kpi.failed - best.kpi.failed)} fewer unserved requests</span></div>
      <dl className="m8-plan-outcome">
        <div><dt>Unserved requests</dt><dd><s>{count.format(run.kpi.failed)}</s><span aria-hidden="true"> → </span><span className="m8-sr-only"> reduced to </span>{count.format(best.kpi.failed)}</dd></div>
        <div><dt>Rentals served</dt><dd>{count.format(best.kpi.served)} <small>of {count.format(best.kpi.requests)}</small></dd></div>
        <div><dt>Average wait <small>(served only)</small></dt><dd>{best.kpi.served ? <>{wait.format(best.kpi.meanWaitMins)} <small>min</small></> : "—"}</dd></div>
      </dl>
      <ol className="m8-plan-actions">{best.actions.map((a, i) => <li key={i}>
        <span className="m8-action-type">{a.action === "reroute" ? "Reroute requests" : "Move vehicles"}</span>
        <strong>{action(a)}</strong><p>{a.reason}</p>
      </li>)}</ol>
      {best.actions.some(a => a.action === "reroute") && <p className="m8-footnote">Rerouting changes where requests are served. It does not relocate vehicles.</p>}
    </div> : <div className="m8-agent-empty"><h3>{state ? "No Improved Plan Yet" : "Explore a Better Plan"}</h3><p>{state
      ? "No valid tool result has improved this scenario. You can give AI another attempt."
      : "AI can test rerouting requests or moving vehicles, read the calculated KPIs, and refine its next proposal."}</p></div>}
    <div className="m8-retry-bar">
      <div><strong>{state ? "Continue This Saved Scenario" : "Start Plan Exploration"}</strong><p>Up to 3 more rounds. AI may stop early.</p></div>
      <button className="m8-button primary" disabled={busy || !configured || dispatchStarted} onClick={onRetry}>{busy ? "Exploring…" : state ? "Retry · +3 Rounds" : "Start AI Planning"}</button>
    </div>
    <p className="m8-footnote m8-run-context">{run.scenario.name} · {count.format(run.kpi.requests)} requests · seed {run.scenario.seed}. Retry uses this saved scenario and snapshot, including previous attempts. Form edits apply only when you run a new scenario.</p>
    {!configured && <p className="m8-alert">AI is unavailable until a provider is configured.</p>}
    {dispatchStarted && <p className="m8-footnote">Dispatch has started. Run a new scenario to explore a different plan.</p>}
    {state?.rounds.length ? <details className="m8-planning-history"><summary>Review Planning History <span>{state.totalRounds} rounds{state.totalRounds > state.rounds.length ? " · latest 60 shown" : ""}</span></summary>
      <ol className="m8-agent-rounds">{state.rounds.map(r => <li key={r.number}><details>
        <summary><span>Round {r.number}</span><span className={`m8-round-status ${r.valid ? "tested" : ""}`}>{r.valid ? "Simulated" : r.tool ? "Rejected" : "No tool call"}</span>{r.valid && r.kpi && <span className="m8-round-kpi">{count.format(r.kpi.served)} served · {count.format(r.kpi.failed)} unserved</span>}</summary>
        {r.actions.map((a, i) => <p key={i}><strong>{action(a)}</strong> — {a.reason}</p>)}
        {r.valid && r.kpi && <p>Average wait for served requests: {r.kpi.served ? `${wait.format(r.kpi.meanWaitMins)} min` : "not applicable"}.</p>}
        <p className="m8-footnote">{r.feedback}</p>
      </details></li>)}</ol>
    </details> : null}
    <details className="m8-agent-limits"><summary>Simulation Limits</summary><p>Every candidate uses the same snapshot, demand, seed and time settings. Parking uses provisional M1 observations. M4 parking reservations and M7 vehicle movements must be revalidated before execution.</p>{best && <p>{best.feedback}</p>}</details>
    <p className="m8-preview-note">Preview only. Exploring or retrying a plan never changes live vehicles.</p>
  </section>;
}
