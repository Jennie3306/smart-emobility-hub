import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  incidentApi, incidentError, incidentSeverities, incidentStatuses, reasonsFor,
  type Incident, type IncidentAction, type IncidentResource, type IncidentSeverity,
} from "../api/incidentApi";
import { StatusBadge } from "@/shared/ui";

const actionNames: Record<IncidentAction, string> = { ASSIGN: "Assign", START: "Start work", RESOLVE: "Mark resolved", REOPEN: "Reopen", CLOSE: "Close" };
const resources: Record<IncidentResource, string> = { VEHICLE: "Vehicle", CHARGING_POINT: "Charging point" };
const time = (value: string | null) => value ? new Date(value).toLocaleString() : "—";

function ReportForm({ onDone }: { onDone: (message: string) => void }) {
  const [type, setType] = useState<IncidentResource>("CHARGING_POINT");
  const [reason, setReason] = useState("OFFLINE");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    setSending(true); setError(null);
    try {
      const created = await incidentApi.report({
        hubId: Number(f.get("hubId")), resourceType: type, resourceId: Number(f.get("resourceId")),
        reason, severity: String(f.get("severity")) as IncidentSeverity,
        description: String(f.get("description") ?? "").trim() || undefined,
      });
      onDone(`Incident #${created.id} recorded. The owning module was notified of the failure and took the resource out of service.`);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setSending(false);
    }
  }

  return <section className="m6-panel" aria-labelledby="report-title">
    <h2 id="report-title">Report an incident</h2>
    <form className="m6-form" onSubmit={submit}>
      <label htmlFor="inc-hub">Hub ID</label><input id="inc-hub" name="hubId" type="number" min={1} required defaultValue={1} />
      <label htmlFor="inc-type">Resource type</label>
      <select id="inc-type" value={type} onChange={e => { const t = e.target.value as IncidentResource; setType(t); setReason(reasonsFor[t][0]); }}>
        <option value="VEHICLE">Vehicle</option><option value="CHARGING_POINT">Charging point</option>
      </select>
      <label htmlFor="inc-resource">Resource ID</label><input id="inc-resource" name="resourceId" type="number" min={1} required />
      <label htmlFor="inc-reason">Reason</label>
      <select id="inc-reason" value={reason} onChange={e => setReason(e.target.value)}>
        {reasonsFor[type].map(r => <option key={r}>{r}</option>)}
      </select>
      <label htmlFor="inc-description">Description{reason === "MANUAL_REPORT" ? " (required)" : ""}</label>
      <textarea id="inc-description" name="description" rows={3} required={reason === "MANUAL_REPORT"} />
      <label htmlFor="inc-severity">Incident severity</label>
      <select id="inc-severity" name="severity" required defaultValue="">
        <option value="" disabled>Choose a severity</option>
        {incidentSeverities.map(value => <option key={value}>{value}</option>)}
      </select>
      <p className="m6-muted">Required; there is no default severity (BR-M6-10).</p>
      {error && <p className="m6-error" role="alert">{error}</p>}
      <button className="m6-primary" type="submit" disabled={sending}>{sending ? "Sending…" : "Submit report"}</button>
    </form>
  </section>;
}

export function IncidentPanel() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [status, setStatus] = useState("");
  const [resource, setResource] = useState("");
  const [severity, setSeverity] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [assignee, setAssignee] = useState("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reporting, setReporting] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setIncidents(await incidentApi.list({ status, resourceType: resource, severity }));
      setError(null);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setLoading(false);
    }
  }, [status, resource, severity]);
  useEffect(() => { load(); }, [load]);

  const selected = incidents.find(i => i.id === selectedId) ?? null;

  async function transition(action: IncidentAction) {
    if (!selected) return;
    setBusy(true); setMessage("");
    try {
      const updated = await incidentApi.transition(selected, action, action === "ASSIGN" ? assignee.trim() : undefined, note.trim() || undefined);
      setMessage(`Incident #${updated.id}: ${actionNames[action]} → ${updated.status}`);
      setAssignee(""); setNote(""); setError(null);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setBusy(false);
      await load();
    }
  }

  return <section aria-labelledby="incident-title">
    <div className="m6-toolbar">
      <h2 id="incident-title">Device incidents</h2>
      <button className="m6-secondary" type="button" aria-expanded={reporting} onClick={() => setReporting(!reporting)}>{reporting ? "Hide report form" : "Report an incident"}</button>
      <button className="m6-secondary" type="button" onClick={load}>Reload</button>
    </div>
    {reporting && <ReportForm onDone={text => { setMessage(text); setReporting(false); load(); }} />}
    {error && <p className="m6-error" role="alert">{error}</p>}
    <p role="status" aria-live="polite">{message}</p>
    <div className="m6-panel">
      <div className="m6-panel-title"><h2>Incident register</h2><span>{incidents.length} incident{incidents.length === 1 ? "" : "s"}</span></div>
      <div className="m6-filters">
        <label>Status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">All statuses</option>{incidentStatuses.map(v => <option key={v}>{v}</option>)}</select></label>
        <label>Resource<select value={resource} onChange={e => setResource(e.target.value)}><option value="">All resources</option>{Object.entries(resources).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label>Severity<select value={severity} onChange={e => setSeverity(e.target.value)}><option value="">All severities</option>{incidentSeverities.map(v => <option key={v}>{v}</option>)}</select></label>
      </div>
      {loading ? <p className="m6-empty">Loading…</p> : incidents.length === 0
        ? <p className="m6-empty" role="status">No incident matches the filters.</p>
        : <div className="m6-table-wrap"><table>
          <thead><tr><th>Incident</th><th>Resource / hub</th><th>Reason</th><th>Severity</th><th>Assignee</th><th>Status</th></tr></thead>
          <tbody>{incidents.map(i => <tr key={i.id} className={i.id === selectedId ? "m6-selected" : ""}>
            <td><button type="button" className="m6-link" aria-label={`Open incident ${i.id}`} onClick={() => { setSelectedId(i.id); setAssignee(""); setNote(""); setMessage(""); }}>#{i.id}</button>
              <small>{i.source === "DEVICE" ? "From a device" : `Reported by ${i.reportedBy}`}</small></td>
            <td>{resources[i.resourceType]} #{i.resourceId}<small>Hub #{i.hubId}</small></td>
            <td>{i.reason}</td><td><StatusBadge status={i.severity} /></td><td>{i.assignee || "Unassigned"}</td>
            <td><StatusBadge status={i.status} /></td>
          </tr>)}</tbody>
        </table></div>}
    </div>
    {selected && <section className="m6-panel m6-detail" aria-labelledby="incident-detail-title">
      <div className="m6-panel-title"><h2 id="incident-detail-title">Incident #{selected.id}</h2><StatusBadge status={selected.status} /></div>
      {selected.description && <p>{selected.description}</p>}
      <dl className="m6-facts">
        <div><dt>Resource</dt><dd>{resources[selected.resourceType]} #{selected.resourceId} · Hub #{selected.hubId}</dd></div>
        <div><dt>Reason / severity</dt><dd>{selected.reason} · {selected.severity}</dd></div>
        <div><dt>Reported</dt><dd>{time(selected.reportedAt)} · {selected.reportedBy}</dd></div>
        <div><dt>Assigned / started</dt><dd>{time(selected.assignedAt)} · {time(selected.startedAt)}</dd></div>
        <div><dt>Resolved / closed</dt><dd>{time(selected.resolvedAt)} · {time(selected.closedAt)}</dd></div>
        <div><dt>Version</dt><dd>{selected.version}</dd></div>
        {selected.note && <div><dt>Latest note</dt><dd>{selected.note}</dd></div>}
      </dl>
      {selected.allowedActions.includes("ASSIGN") && <label className="m6-assignee">Assignee (operator username)
        <input value={assignee} onChange={e => setAssignee(e.target.value)} placeholder="e.g. operator1" /></label>}
      {selected.allowedActions.length > 0 && <label className="m6-assignee">Note (optional)
        <input value={note} onChange={e => setNote(e.target.value)} /></label>}
      <div className="m6-toolbar">{selected.allowedActions.map(action => <button className="m6-secondary" type="button" key={action}
        disabled={busy || (action === "ASSIGN" && !assignee.trim())} onClick={() => transition(action)}>{actionNames[action]}</button>)}
        {selected.status === "CLOSED" && <p>The incident is closed; no further action is possible (BR-M6-04).</p>}
      </div>
      <p className="m6-muted">Resolving an incident does not bring the device back by itself: the device returns to service with its next healthy reading (for example a NORMAL_TICK run), and the owning module decides its state.</p>
    </section>}
  </section>;
}
