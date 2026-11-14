import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { monitoringApi } from "@/modules/m1monitoring/api/monitoringApi";
import type { HubSummary } from "@/modules/m1monitoring/api/types";
import { vehicleApi, type Vehicle } from "@/modules/m3vehicle/api/vehicleApi";
import {
  nextStatuses, redistributionApi, redistributionError,
  type Recommendation, type RedistributionTask, type TaskStatus,
} from "../api/redistributionApi";
import { Notice, PageHeader, PageSkeleton, StatCard, StatusBadge } from "@/shared/ui";

/**
 * M7 - Vehicle redistribution (Operator only).
 * FR-M7-02: review proposals (from M1 alerts or the What-if module) and approve or reject them.
 * FR-M7-01: create a task by hand. FR-M7-03: follow a task through CREATED -> IN_PROGRESS -> DONE / FAILED.
 */
const actionLabel: Record<TaskStatus, string> = {
  IN_PROGRESS: "Start", DONE: "Mark done", FAILED: "Mark failed", CREATED: "",
};
const sourceLabel: Record<string, string> = { M1_ALERT: "Hub alert (M1)", M8_SIMULATION: "What-if (M8)" };

function Badge({ value }: { value: string }) {
  return <StatusBadge status={value} />;
}

export default function RedistributionPage() {
  const [hubs, setHubs] = useState<HubSummary[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [tasks, setTasks] = useState<RedistributionTask[]>([]);
  const [recs, setRecs] = useState<Recommendation[]>([]);
  const [filter, setFilter] = useState<TaskStatus | "">("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [form, setForm] = useState({ source: "", vehicle: "", destination: "" });

  const load = useCallback(async () => {
    try {
      const [h, v, t, r] = await Promise.all([
        monitoringApi.getHubs(), vehicleApi.list(),
        redistributionApi.tasks(filter || undefined), redistributionApi.recommendations(),
      ]);
      setHubs(h); setVehicles(v); setTasks(t); setRecs(r); setError(null);
    } catch (e) {
      setError(redistributionError(e));
    } finally {
      setLoaded(true);
    }
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  const hubName = useMemo(() => {
    const names = new Map(hubs.map(h => [h.id, h.name]));
    return (id: number) => names.get(id) ?? `Hub #${id}`;
  }, [hubs]);
  const vehicleCode = useMemo(() => {
    const codes = new Map(vehicles.map(v => [v.id, v.code]));
    return (id: number) => codes.get(id) ?? `#${id}`;
  }, [vehicles]);
  const movable = vehicles.filter(v => v.state === "AVAILABLE" && String(v.hubId) === form.source);
  const open = recs.filter(r => r.status === "PENDING" || r.status === "PARTIAL");
  const decided = recs.filter(r => r.status !== "PENDING" && r.status !== "PARTIAL").slice(0, 5);

  async function run(key: string, action: () => Promise<string | null>) {
    setBusy(key); setNotice(null);
    try {
      const message = await action();
      setError(null);
      if (message) setNotice(message);
      await load();
    } catch (e) {
      setError(redistributionError(e));
    } finally {
      setBusy(null);
    }
  }

  const approve = (r: Recommendation) => run(`rec-${r.id}`, async () => {
    const result = await redistributionApi.approve(r.id);
    const made = `${result.taskIds.length} task(s) created`;
    return result.message ? `${made}. ${result.message}` : `${made}. Start each task when the vehicle leaves.`;
  });
  const reject = (r: Recommendation) => {
    const reason = window.prompt("Reason for rejecting this recommendation (optional)") ?? null;
    if (reason === null) return;
    run(`rec-${r.id}`, async () => { await redistributionApi.reject(r.id, reason); return "Recommendation rejected."; });
  };
  const move = (t: RedistributionTask, status: TaskStatus) =>
    run(`task-${t.taskId}`, async () => { await redistributionApi.moveTask(t.taskId, status); return null; });

  function create(e: FormEvent) {
    e.preventDefault();
    run("create", async () => {
      const t = await redistributionApi.createTask(Number(form.vehicle), Number(form.source), Number(form.destination));
      setForm({ source: "", vehicle: "", destination: "" });
      return `Task #${t.taskId} created for ${vehicleCode(t.vehicleId)}.`;
    });
  }

  if (!loaded) return <PageSkeleton />;

  const counts = (st: TaskStatus) => tasks.filter(t => t.status === st).length;

  return (
    <div className="space-y-6">
      <PageHeader module="M7" eyebrow="Vehicle redistribution" title="Vehicle redistribution"
        description="Approve proposals to move vehicles between hubs, then follow each task until the vehicle arrives."
        actions={<button className="btn btn-secondary" onClick={load}>↻ Refresh</button>} />

      {error && <Notice kind="error" action={<button className="btn btn-secondary btn-sm" onClick={load}>Retry</button>}>{error}</Notice>}
      {notice && <Notice kind="success" onClose={() => setNotice(null)}>{notice}</Notice>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Pending recommendations" value={open.length} detail="Approval required" icon="⇄" tone="amber" />
        <StatCard label="Tasks in progress" value={counts("IN_PROGRESS")} detail="Vehicles on their way" icon="↔" />
        <StatCard label="Completed tasks" value={counts("DONE")} detail="Vehicle docked at the destination" icon="✓" tone="green" />
        <StatCard label="Failed tasks" value={counts("FAILED")} detail="Vehicle returned to its source" icon="✕" tone="red" />
      </div>

      <section className="card p-5">
        <h2 className="mb-3 text-[15px] font-semibold text-slate-900">Recommendations waiting for a decision</h2>
        {open.length === 0
          ? <p className="text-sm text-slate-500">No open recommendation. A hub alert from the dashboard or a What-if run will add one here.</p>
          : <div className="overflow-x-auto"><table className="table-ui">
              <thead><tr>
                <th>Move</th><th>Vehicles</th><th>Source</th>
                <th>Why</th><th>Status</th><th /></tr></thead>
              <tbody>{open.map(r => <tr key={r.id} className="align-top">
                <td className="font-medium">{hubName(r.sourceHubId)} → {hubName(r.destinationHubId)}</td>
                <td className="tabular-nums">{r.taskCount} / {r.amount}</td>
                <td className="text-xs">{sourceLabel[r.source] ?? r.source}</td>
                <td className="text-xs text-slate-600">{r.reason}{r.note && <div className="mt-1 text-amber-700">{r.note}</div>}</td>
                <td><Badge value={r.status} /></td>
                <td><div className="flex gap-2">
                  <button disabled={busy !== null} onClick={() => approve(r)} className="btn btn-primary btn-sm">Approve</button>
                  {r.status === "PENDING" && <button disabled={busy !== null} onClick={() => reject(r)} className="btn btn-secondary btn-sm">Reject</button>}
                </div></td>
              </tr>)}</tbody></table></div>}
        {decided.length > 0 && <details className="mt-3 text-sm"><summary className="cursor-pointer text-slate-600">Recently decided ({decided.length})</summary>
          <ul className="mt-2 space-y-1 text-xs text-slate-600">{decided.map(r => <li key={r.id}>
            <Badge value={r.status} /> {hubName(r.sourceHubId)} → {hubName(r.destinationHubId)}, {r.taskCount}/{r.amount} vehicle(s){r.resolvedBy && ` · ${r.resolvedBy}`}{r.note && ` · ${r.note}`}</li>)}</ul></details>}
      </section>

      <section className="card p-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-[15px] font-semibold text-slate-900">Redistribution tasks</h2>
          <label className="label">Status{" "}
            <select className="input mt-0 inline-block w-auto" value={filter} onChange={e => setFilter(e.target.value as TaskStatus | "")}>
              <option value="">All</option>{(["CREATED", "IN_PROGRESS", "DONE", "FAILED"] as TaskStatus[]).map(s => <option key={s}>{s}</option>)}
            </select></label>
        </div>
        {tasks.length === 0
          ? <p className="text-sm text-slate-500">No task{filter ? ` in ${filter}` : ""} yet.</p>
          : <div className="overflow-x-auto"><table className="table-ui">
              <thead><tr>
                <th>Task</th><th>Vehicle</th><th>From → To</th>
                <th>Status</th><th>Created</th><th /></tr></thead>
              <tbody>{tasks.map(t => <tr key={t.taskId}>
                <td className="font-mono">#{t.taskId}{t.recommendationId && <div className="text-xs text-slate-500">rec. {t.recommendationId}</div>}</td>
                <td>{vehicleCode(t.vehicleId)}</td>
                <td>{hubName(t.sourceHubId)} → {hubName(t.destinationHubId)}</td>
                <td><Badge value={t.status} /></td>
                <td className="text-xs">{new Date(t.createdAt).toLocaleString()}</td>
                <td><div className="flex gap-2">{nextStatuses[t.status].map(s =>
                  <button key={s} disabled={busy !== null} onClick={() => move(t, s)}
                    className={`btn btn-sm ${s === "FAILED" ? "btn-danger" : "btn-primary"}`}>{actionLabel[s]}</button>)}
                </div></td>
              </tr>)}</tbody></table></div>}
      </section>

      <section className="card p-5">
        <h2 className="mb-3 text-[15px] font-semibold text-slate-900">New task</h2>
        <form className="grid gap-3 md:grid-cols-4" onSubmit={create}>
          <label className="label">From hub
            <select required className="input" value={form.source}
              onChange={e => setForm({ source: e.target.value, vehicle: "", destination: form.destination })}>
              <option value="">Choose…</option>{hubs.map(h => <option key={h.id} value={h.id}>{h.name} ({h.availableVehicles} available)</option>)}
            </select></label>
          <label className="label">Vehicle
            <select required className="input" value={form.vehicle} disabled={!form.source}
              onChange={e => setForm({ ...form, vehicle: e.target.value })}>
              <option value="">{form.source && movable.length === 0 ? "No available vehicle" : "Choose…"}</option>
              {movable.map(v => <option key={v.id} value={v.id}>{v.code} · {v.batteryLevel}%</option>)}
            </select></label>
          <label className="label">To hub
            <select required className="input" value={form.destination}
              onChange={e => setForm({ ...form, destination: e.target.value })}>
              <option value="">Choose…</option>{hubs.filter(h => String(h.id) !== form.source).map(h =>
                <option key={h.id} value={h.id}>{h.name} ({h.utilisation.freeSlots} free spaces)</option>)}
            </select></label>
          <div className="flex items-end"><button disabled={busy !== null} className="btn btn-primary w-full">Create task</button></div>
        </form>
        <p className="mt-2 text-xs text-slate-500">The system checks that the vehicle is free (BR-M7-01), has no other task (BR-M7-02) and that the destination still has a free space after the vehicles already on their way (BR-M7-03).</p>
      </section>
    </div>
  );
}
