import { useEffect, useRef, useState } from "react";
import { StatusBadge } from "@/shared/ui";
import { describeApiError, normalizeSeed, scenarios, simulatorApi, type Scenario, type SimulationRun, type SimulatorStatus } from "../api/simulatorApi";

const labels: Record<Scenario, string> = {
  NORMAL_TICK: "Normal device update",
  VEHICLE_FAILURE: "Vehicle device failure",
  CHARGER_FAILURE: "Charging point failure",
};
const states = { IDLE: "No run yet", RUNNING: "Running", SUCCEEDED: "Completed", FAILED: "Failed" };
type Failure = ReturnType<typeof describeApiError>;

function ApiFailure({ failure }: { failure: Failure }) {
  return <div className="m6-error" role="alert">
    <strong>{failure.status ? `HTTP ${failure.status} · ` : ""}{failure.message}</strong>
    {failure.code && <p className="m6-mono">{failure.code}</p>}
    {failure.details && Object.keys(failure.details).length > 0 && <pre>{JSON.stringify(failure.details, null, 2)}</pre>}
    {(failure.status === 401 || failure.status === 403) && <a href="/login">Sign in with an Operator account</a>}
  </div>;
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("en-GB") : "—";
}

function RunReceipt({ run }: { run: SimulationRun }) {
  return <section className="m6-panel" aria-labelledby="run-result-title">
    <div className="m6-panel-title"><h2 id="run-result-title">Latest run</h2>
      <StatusBadge status={run.error ? "FAILED" : run.completedAt ? "SUCCEEDED" : "RUNNING"}>{run.error ? "Failed" : run.completedAt ? "Completed" : "Running"}</StatusBadge></div>
    <p className="m6-mono m6-run-id">{run.runId}</p>
    <dl className="m6-facts">
      <div><dt>Scenario</dt><dd>{run.scenario}</dd></div><div><dt>Seed</dt><dd className="m6-mono">{run.seed}</dd></div>
      <div><dt>Started</dt><dd>{date(run.startedAt)}</dd></div><div><dt>Finished</dt><dd>{date(run.completedAt)}</dd></div>
    </dl>
    <dl className="m6-counts">
      <div><dt>Readings</dt><dd>{run.readingCount}</dd></div><div><dt>Processed</dt><dd>{run.processedCount}</dd></div>
      <div><dt>Duplicate inbound ID</dt><dd>{run.duplicateCount}</dd></div><div><dt>Rejected</dt><dd>{run.rejectedCount}</dd></div>
    </dl>
    {run.error && <p className="m6-error" role="alert">Run failed: {run.error}</p>}
    <h3>Events published by this run</h3>
    {run.outboundEvents.length === 0 ? <p className="m6-muted">This run published no events.</p> :
      <ul className="m6-events">{run.outboundEvents.map(event => <li key={event.eventId}>
        <strong>{event.eventType}</strong><span>{event.resourceType} / {event.resourceId}</span>
        <code>{event.eventId}</code>
      </li>)}</ul>}
  </section>;
}

export function SimulatorPanel() {
  const [status, setStatus] = useState<SimulatorStatus | null>(null);
  const [statusError, setStatusError] = useState<Failure | null>(null);
  const [runError, setRunError] = useState<Failure | null>(null);
  const [seedError, setSeedError] = useState("");
  const [seed, setSeed] = useState("100");
  const [scenario, setScenario] = useState<Scenario>("NORMAL_TICK");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  const statusRequest = useRef<AbortController | null>(null);
  const mounted = useRef(false);

  async function loadStatus() {
    statusRequest.current?.abort();
    const request = new AbortController();
    statusRequest.current = request;
    setLoading(true);
    try {
      const result = await simulatorApi.status(request.signal);
      if (!request.signal.aborted && mounted.current) { setStatus(result); setStatusError(null); }
    } catch (error) {
      if (!request.signal.aborted && mounted.current) setStatusError(describeApiError(error));
    } finally {
      if (!request.signal.aborted && mounted.current) setLoading(false);
    }
  }

  useEffect(() => {
    mounted.current = true;
    void loadStatus();
    return () => { mounted.current = false; statusRequest.current?.abort(); };
  }, []);

  async function run(event: React.FormEvent) {
    event.preventDefault();
    if (inFlight.current || loading || !status || statusError || status.state === "RUNNING") return;
    try { normalizeSeed(seed); setSeedError(""); } catch (error) {
      setSeedError(error instanceof Error ? error.message : "Invalid seed."); return;
    }
    inFlight.current = true;
    setSubmitting(true); setRunError(null);
    try {
      const result = await simulatorApi.run(scenario, seed);
      if (!mounted.current) return;
      setStatus(previous => previous && ({ ...previous, state: result.error ? "FAILED" : "SUCCEEDED", latestRun: result }));
      await loadStatus();
    } catch (error) {
      if (!mounted.current) return;
      const failure = describeApiError(error);
      setRunError(failure);
      // Refresh state after a concurrent run or server failure; never retry POST.
      if (failure.status === 409 || !failure.status || failure.status >= 500) await loadStatus();
    } finally {
      inFlight.current = false;
      if (mounted.current) setSubmitting(false);
    }
  }

  const blocked = loading || submitting || !status || !!statusError || status.state === "RUNNING";
  return <div className="m6-grid">
    <section className="m6-panel" aria-labelledby="simulator-title">
      <div className="m6-panel-title"><h2 id="simulator-title">Simulator control</h2>
        <button className="m6-secondary" type="button" disabled={loading || submitting} onClick={() => void loadStatus()}>Refresh status</button></div>
      <p className="m6-muted">Each run sends one scenario and one seed to the backend. The scheduler is shown for information only.</p>
      <div className="m6-live" aria-live="polite">
        {loading && <p role="status">Loading status…</p>}
        {status && <><StatusBadge status={status.state}>{states[status.state]}</StatusBadge><dl className="m6-facts">
          <div><dt>Scheduler</dt><dd>{status.schedulerEnabled ? "On" : "Off"}</dd></div>
          <div><dt>Current interval</dt><dd>{status.intervalMs === null ? "—" : `${status.intervalMs.toLocaleString("en-GB")} ms`}</dd></div>
        </dl></>}
      </div>
      {statusError && <><ApiFailure failure={statusError} /><p className="m6-muted">The latest status could not be confirmed; the data shown may be out of date.</p></>}
      {status?.state === "RUNNING" && <p className="m6-notice">The simulator is busy. Refresh the status to see when the run has finished.</p>}
      <form onSubmit={run} className="m6-form">
        <label htmlFor="m6-scenario">Scenario</label>
        <select id="m6-scenario" value={scenario} disabled={submitting} onChange={e => setScenario(e.target.value as Scenario)}>
          {scenarios.map(value => <option key={value} value={value}>{labels[value]}</option>)}
        </select>
        <label htmlFor="m6-seed">Seed</label>
        <input id="m6-seed" type="text" inputMode="text" value={seed} disabled={submitting} spellCheck={false}
          aria-invalid={!!seedError} aria-describedby="m6-seed-help m6-seed-error" onChange={e => { setSeed(e.target.value); setSeedError(""); }} />
        <p id="m6-seed-help" className="m6-muted">A whole number (Java long). The same seed reproduces the same readings; every run still gets a new ID.</p>
        <p id="m6-seed-error" className="m6-field-error" role={seedError ? "alert" : undefined}>{seedError}</p>
        <button className="m6-primary" type="submit" disabled={blocked}>{submitting ? "Running…" : "Run scenario"}</button>
      </form>
      {runError && <ApiFailure failure={runError} />}
    </section>
    {status?.latestRun ? <RunReceipt run={status.latestRun} /> : <section className="m6-panel m6-empty">
      <h2>Run result</h2><p>{loading ? "Reading backend data…" : statusError ? "The result could not be loaded." : "No run yet in this backend session."}</p>
      <p className="m6-muted">Choose a scenario and a seed to start. The result shows the counts and the event IDs returned by the backend.</p>
    </section>}
  </div>;
}
