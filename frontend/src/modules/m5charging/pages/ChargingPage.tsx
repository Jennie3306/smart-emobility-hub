import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  chargingApi, chargingErrorMessage, currentRole,
  type ChargingHub, type ChargingSession, type PriorityLevel,
} from "../api/chargingApi";
import { BatteryMeter, Notice, PageHeader, PageSkeleton, StatCard, StatusBadge } from "@/shared/ui";

const statusTone: Record<string, string> = {
  REQUESTED: "bg-slate-100 text-slate-700",
  QUEUED: "bg-slate-100 text-slate-700",
  SCHEDULED: "bg-blue-100 text-blue-800",
  CHARGING: "bg-brand/10 text-brand",
  COMPLETED: "bg-green-100 text-green-800",
  INTERRUPTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-slate-100 text-slate-500 line-through",
};

function Battery({ value }: { value: number }) {
  return <BatteryMeter level={value} width="w-20" />;
}

function SessionStatus({ status }: { status: string }) {
  return <StatusBadge status={status} />;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : "—";
}

function SessionList({ sessions, onStart, onCancel, busyId }: {
  sessions: ChargingSession[];
  onStart?: (id: number) => void;
  onCancel?: (id: number) => void;
  busyId: number | null;
}) {
  if (!sessions.length) return <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
    No charging sessions yet. A request will appear here after it is submitted.
  </div>;

  return <div className="card overflow-x-auto">
    <table className="table-ui">
      <thead>
        <tr><th>Session</th><th>Vehicle</th><th>Battery</th><th>Priority</th><th>Status</th><th>Port / Queue</th><th>Schedule</th><th>Actions</th></tr>
      </thead>
      <tbody>
        {sessions.map(session => <tr key={session.id}>
          <td className="font-mono">CS-{session.id}</td>
          <td><div className="font-medium">{session.vehicleKind} #{session.vehicleId}</div><div className="text-xs text-slate-500">Hub #{session.hubId}</div></td>
          <td><Battery value={session.batteryLevel} /></td>
          <td><StatusBadge status={session.priorityLevel} /><div className="text-xs text-slate-500">P {session.priorityScore.toFixed(1)}</div></td>
          <td><SessionStatus status={session.status} /></td>
          <td className="text-xs">{session.chargingPointId ? `CP-${session.chargingPointId}` : session.queuePosition ? `Queue #${session.queuePosition}` : "—"}</td>
          <td className="text-xs">{session.scheduledStartAt ? <>{formatDate(session.scheduledStartAt)}<div className="text-slate-500">to {formatDate(session.scheduledEndAt)}</div></> : <>{session.waitingMinutes} min waiting{session.estimatedWaitMinutes != null && <> · ~{session.estimatedWaitMinutes} min ETA</>}</>}</td>
          <td><div className="flex gap-2">
            {onStart && session.status === "SCHEDULED" && <button disabled={busyId === session.id} className="btn btn-primary btn-sm" onClick={() => onStart(session.id)}>Start</button>}
            {onCancel && ["REQUESTED", "QUEUED", "SCHEDULED", "INTERRUPTED"].includes(session.status) && <button disabled={busyId === session.id} className="btn btn-danger btn-sm" onClick={() => onCancel(session.id)}>Cancel</button>}
          </div></td>
        </tr>)}
      </tbody>
    </table>
  </div>;
}

function toDateTimeLocal(value: string) {
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export default function ChargingPage() {
  const [role, setRole] = useState<"STUDENT" | "OPERATOR" | null>(null);
  const [hubs, setHubs] = useState<ChargingHub[]>([]);
  const [hubId, setHubId] = useState<number | null>(null);
  const [sessions, setSessions] = useState<ChargingSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [vehicleKind, setVehicleKind] = useState<"RENTAL" | "PERSONAL">("RENTAL");
  const [vehicleId, setVehicleId] = useState("1");
  const [batteryLevel, setBatteryLevel] = useState("18");
  const [expectedUseAt, setExpectedUseAt] = useState("");
  const [parkingSpaceId, setParkingSpaceId] = useState("");
  const [scheduleStart, setScheduleStart] = useState("");
  const [duration, setDuration] = useState("60");

  const [prioritySession, setPrioritySession] = useState<ChargingSession | null>(null);
  const [newPriority, setNewPriority] = useState<PriorityLevel>("HIGH");
  const [reason, setReason] = useState("");
  const [moveStart, setMoveStart] = useState("");
  const [moveDuration, setMoveDuration] = useState("60");

  const selectedHub = useMemo(() => hubs.find(h => h.hubId === hubId) ?? null, [hubs, hubId]);

  const load = useCallback(async (currentRole: "STUDENT" | "OPERATOR", selectedHubId: number | null) => {
    if (currentRole === "STUDENT") {
      setSessions(await chargingApi.mine());
    } else if (selectedHubId) {
      setSessions(await chargingApi.queue(selectedHubId));
    }
  }, []);

  useEffect(() => {
    let active = true;
    async function initialise() {
      try {
        const [userRole, hubList] = await Promise.all([currentRole(), chargingApi.hubs()]);
        if (!active) return;
        setRole(userRole);
        setHubs(hubList);
        setHubId(hubList[0]?.hubId ?? null);
        if (userRole === "STUDENT") setSessions(await chargingApi.mine());
        else if (hubList[0]) setSessions(await chargingApi.queue(hubList[0].hubId));
      } catch (e) {
        if (active) setError(chargingErrorMessage(e));
      } finally {
        if (active) setLoading(false);
      }
    }
    initialise();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!role || (role === "OPERATOR" && !hubId)) return;
    let active = true;
    const refresh = async () => {
      try { const data = role === "STUDENT" ? await chargingApi.mine() : await chargingApi.queue(hubId!); if (active) setSessions(data); }
      catch (e) { if (active) setError(chargingErrorMessage(e)); }
    };
    const timer = window.setInterval(refresh, 15000);
    return () => { active = false; window.clearInterval(timer); };
  }, [role, hubId]);

  async function refreshNow() {
    if (!role) return;
    setBusy(true);
    setError(null);
    try { await load(role, hubId); }
    catch (e) { setError(chargingErrorMessage(e)); }
    finally { setBusy(false); }
  }

  async function submitRequest(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(null); setNotice(null);
    try {
      await chargingApi.request({
        hubId: hubId!, vehicleKind, vehicleId: Number(vehicleId), batteryLevel: Number(batteryLevel),
        ...(parkingSpaceId.trim() ? { parkingSpaceId: Number(parkingSpaceId) } : {}),
        ...(expectedUseAt ? { expectedUseAt: new Date(expectedUseAt).toISOString() } : {}),
      });
      setNotice("Charging request accepted. The priority queue is recalculated automatically.");
      await load("STUDENT", hubId);
    } catch (e) { setError(chargingErrorMessage(e)); }
    finally { setBusy(false); }
  }

  async function submitSchedule(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(null); setNotice(null);
    try {
      await chargingApi.schedule({
        hubId: hubId!, vehicleId: Number(vehicleId), batteryLevel: Number(batteryLevel),
        parkingSpaceId: Number(parkingSpaceId), startAt: new Date(scheduleStart).toISOString(),
        durationMinutes: Number(duration),
      });
      setNotice("Charging schedule confirmed. The port hold expires 10 minutes after its start time.");
      await load("STUDENT", hubId);
    } catch (e) { setError(chargingErrorMessage(e)); }
    finally { setBusy(false); }
  }

  async function actOnSession(id: number, action: "start" | "cancel") {
    setBusyId(id); setError(null); setNotice(null);
    try {
      if (action === "start") await chargingApi.start(id); else await chargingApi.cancel(id);
      setNotice(action === "start" ? "Charging session started." : "Charging session cancelled.");
      if (role) await load(role, hubId);
    } catch (e) { setError(chargingErrorMessage(e)); }
    finally { setBusyId(null); }
  }

  async function savePriority(event: FormEvent) {
    event.preventDefault();
    if (!prioritySession) return;
    setBusyId(prioritySession.id); setError(null); setNotice(null);
    try {
      await chargingApi.changePriority(prioritySession.id, newPriority, reason);
      let moved = false;
      if (prioritySession.status === "SCHEDULED" && moveStart) {
        const originalStart = prioritySession.scheduledStartAt ? new Date(prioritySession.scheduledStartAt).getTime() : null;
        const nextStart = new Date(moveStart).getTime();
        if (originalStart !== nextStart) {
          await chargingApi.moveSchedule(prioritySession.id, new Date(moveStart).toISOString(), Number(moveDuration), reason);
          moved = true;
        }
      }
      setNotice(moved
        ? `Priority and schedule for CS-${prioritySession.id} updated. Changes were recorded.`
        : `Priority for CS-${prioritySession.id} updated and recorded in the audit log.`);
      setPrioritySession(null); setReason(""); setMoveStart("");
      await load("OPERATOR", hubId);
    } catch (e) { setError(chargingErrorMessage(e)); }
    finally { setBusyId(null); }
  }

  if (loading) return <PageSkeleton />;
  if (!role) return <Notice kind="error">{error ?? "Unable to identify the signed-in role."}</Notice>;

  return <div className="space-y-6">
    <PageHeader module="M5" eyebrow="Charging queue & smart scheduling" title={role === "OPERATOR" ? "Smart charging operations" : "My charging"}
      description="Priority-based allocation considers battery need, expected vehicle use and waiting time. A session already charging is never pre-empted."
      actions={<>
        {role === "OPERATOR" && <label className="flex items-center gap-2 text-sm font-medium text-slate-600">Hub <select className="input mt-0 w-auto" value={hubId ?? ""} onChange={e => setHubId(Number(e.target.value))}>{hubs.map(h => <option key={h.hubId} value={h.hubId}>{h.hubName}</option>)}</select></label>}
        <button className="btn btn-secondary" onClick={refreshNow} disabled={busy}>↻ Refresh</button>
      </>} />

    {error && <Notice kind="error" onClose={() => setError(null)}>{error}</Notice>}
    {notice && <Notice kind="success" onClose={() => setNotice(null)}>{notice}</Notice>}

    {role === "STUDENT" ? <>
      <div className="grid gap-4 xl:grid-cols-2">
        <form onSubmit={submitRequest} className="space-y-4 card p-5">
          <div><h2 className="text-[15px] font-semibold text-slate-900">Request charging</h2><p className="hint mt-1">For a rental vehicle or a registered personal EV.</p></div>
          <label className="label">Hub<select required className="input" value={hubId ?? ""} onChange={e => setHubId(Number(e.target.value))}>{hubs.map(h => <option key={h.hubId} value={h.hubId}>{h.hubName}</option>)}</select></label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="label">Vehicle kind<select className="input" value={vehicleKind} onChange={e => setVehicleKind(e.target.value as "RENTAL" | "PERSONAL")}><option value="RENTAL">Rental</option><option value="PERSONAL">Personal</option></select></label>
            <label className="label">Vehicle ID<input required min="1" type="number" className="input" value={vehicleId} onChange={e => setVehicleId(e.target.value)} /></label>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="label">Battery level (%)<input required min="0" max="100" type="number" className="input" value={batteryLevel} onChange={e => setBatteryLevel(e.target.value)} /></label>
            <label className="label">Expected next use (optional)<input type="datetime-local" className="input" value={expectedUseAt} onChange={e => setExpectedUseAt(e.target.value)} /></label>
          </div>
          <label className="label">Parking space ID (optional)<input min="1" type="number" className="input" value={parkingSpaceId} onChange={e => setParkingSpaceId(e.target.value)} /></label>
          <p className="text-xs text-slate-500">Requests are rejected at 90% battery or higher. Each vehicle may have only one unfinished session.</p>
          <button disabled={busy || !hubId} className="btn btn-primary">{busy ? "Submitting…" : "Submit charging request"}</button>
        </form>

        <form onSubmit={submitSchedule} className="space-y-4 card p-5">
          <div><h2 className="text-[15px] font-semibold text-slate-900">Book a charging schedule</h2><p className="hint mt-1">Personal EV only. The parking reservation must cover the complete charging slot at the same Hub.</p></div>
          <label className="label">Personal vehicle ID<input required min="1" type="number" className="input" value={vehicleId} onChange={e => setVehicleId(e.target.value)} /></label>
          <label className="label">Parking reservation · space ID<input required min="1" type="number" className="input" value={parkingSpaceId} onChange={e => setParkingSpaceId(e.target.value)} /></label>
          <label className="label">Start time<input required type="datetime-local" className="input" value={scheduleStart} onChange={e => setScheduleStart(e.target.value)} /></label>
          <label className="label">Duration<select className="input" value={duration} onChange={e => setDuration(e.target.value)}><option value="30">30 minutes</option><option value="60">60 minutes</option><option value="90">90 minutes</option></select></label>
          <p className="text-xs text-slate-500">Within 24 hours · 30-minute start slots · maximum two active schedules per student.</p>
          <button disabled={busy || !hubId} className="btn btn-primary">{busy ? "Booking…" : "Confirm schedule"}</button>
        </form>
      </div>
      <section className="space-y-3"><div><h2 className="text-[15px] font-semibold text-slate-900">Your charging sessions</h2><p className="text-xs text-slate-500">Queue status refreshes automatically every 15 seconds.</p></div><SessionList sessions={sessions} onStart={id => actOnSession(id, "start")} onCancel={id => actOnSession(id, "cancel")} busyId={busyId} /></section>
    </> : <>
      {selectedHub && <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Points in use" value={`${selectedHub.chargingPoints} / ${selectedHub.totalPoints}`} detail="Currently charging" icon="⚡" />
        <Metric label="Available points" value={selectedHub.availablePoints} detail="Ready for allocation" icon="✓" tone="green" />
        <Metric label="Queued sessions" value={selectedHub.queuedSessions} detail="Waiting for a port" icon="◷" tone="amber" />
        <Metric label="Faulty points" value={selectedHub.faultyPoints} detail="Excluded from allocation" icon="!" tone="red" />
      </div>}
      <section className="space-y-3"><div className="flex flex-wrap items-end justify-between gap-2"><div><h2 className="text-[15px] font-semibold text-slate-900">Hub charging queue</h2><p className="text-xs text-slate-500">Sorted by URGENT, then priority score P, then FIFO.</p></div><span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-200">● Auto-recalculate · 60s</span></div>
        <OperatorQueue sessions={sessions} busyId={busyId} onStart={id => actOnSession(id, "start")} onAdjust={session => {
          setPrioritySession(session); setNewPriority(session.priorityLevel); setReason("");
          setMoveStart(session.scheduledStartAt ? toDateTimeLocal(session.scheduledStartAt) : "");
          setMoveDuration(session.scheduledStartAt && session.scheduledEndAt
            ? String(Math.max(30, Math.min(90, Math.round((new Date(session.scheduledEndAt).getTime() - new Date(session.scheduledStartAt).getTime()) / 60000)))) : "60");
        }} />
      </section>
      {prioritySession && <form onSubmit={savePriority} className="max-w-xl space-y-3 card p-5">
        <div className="flex items-center justify-between"><h2 className="text-[15px] font-semibold text-slate-900">Adjust CS-{prioritySession.id}</h2><button type="button" className="text-sm text-slate-500" onClick={() => setPrioritySession(null)}>Close</button></div>
        <label className="label">New priority<select className="input" value={newPriority} onChange={e => setNewPriority(e.target.value as PriorityLevel)}><option>NORMAL</option><option>HIGH</option><option>URGENT</option></select></label>
        {prioritySession.status === "SCHEDULED" && <div className="grid gap-3 sm:grid-cols-2">
          <label className="label">Move schedule to<input type="datetime-local" className="input" value={moveStart} onChange={e => setMoveStart(e.target.value)} /></label>
          <label className="label">Duration<select className="input" value={moveDuration} onChange={e => setMoveDuration(e.target.value)}><option value="30">30 minutes</option><option value="60">60 minutes</option><option value="90">90 minutes</option></select></label>
        </div>}
        <label className="label">Reason (required)<textarea required minLength={3} maxLength={500} className="input" rows={3} value={reason} onChange={e => setReason(e.target.value)} placeholder="Operational reason for this priority override" /></label>
        <p className="text-xs text-slate-500">A session already in CHARGING cannot be pre-empted. HIGH adds 30 points; URGENT is ranked first. A moved personal schedule must still fit its M4 parking reservation. The operator, time and reason are recorded.</p>
        <button disabled={busyId === prioritySession.id} className="btn btn-primary">Save priority</button>
      </form>}
    </>}

    <section className="card p-5 text-sm text-slate-600">
      <h2 className="text-[15px] font-semibold text-slate-900">Priority policy</h2>
      <p className="mt-2 font-mono text-brand">P = 0.5 × B + 0.3 × U + 0.2 × W</p>
      <p className="mt-1 text-xs">B = 100 − battery (%); U = 100 / 70 / 40 / 0 for next use within 30 / 60 / 120 minutes / later or not given (100 if the expected use time has already passed); W = min(100, 2 × waiting minutes). HIGH adds 30 points; URGENT always ranks first. An interrupted session receives +20 only if no replacement port is available and it remains queued.</p>
    </section>
  </div>;
}

function Metric({ label, value, detail, icon, tone }: { label: string; value: string | number; detail: string; icon?: string; tone?: "brand" | "green" | "amber" | "red" | "slate" }) {
  return <StatCard label={label} value={value} detail={detail} icon={icon} tone={tone} />;
}

function OperatorQueue({ sessions, busyId, onStart, onAdjust }: {
  sessions: ChargingSession[]; busyId: number | null;
  onStart: (id: number) => void; onAdjust: (session: ChargingSession) => void;
}) {
  // Same order as the backend dispatcher (review PR #11): the server's queuePosition first, then
  // URGENT, higher score, earlier request - the tie-breaks of ChargingPriorityPolicy.comparator().
  const queue = [...sessions].filter(s => ["REQUESTED", "QUEUED", "INTERRUPTED"].includes(s.status))
    .sort((a, b) => (a.queuePosition ?? Number.MAX_SAFE_INTEGER) - (b.queuePosition ?? Number.MAX_SAFE_INTEGER)
      || (a.priorityLevel === "URGENT" ? 0 : 1) - (b.priorityLevel === "URGENT" ? 0 : 1)
      || b.priorityScore - a.priorityScore
      || new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  const active = sessions.filter(s => s.status === "CHARGING" || s.status === "SCHEDULED");
  return <div className="space-y-4">
    <div className="card overflow-x-auto"><table className="table-ui"><thead><tr><th>Position</th><th>Vehicle</th><th>Battery</th><th>Score</th><th>Priority</th><th>Waiting</th><th>Action</th></tr></thead><tbody>
      {!queue.length && <tr><td className="p-6 text-center text-slate-500" colSpan={7}>No sessions are waiting at this Hub.</td></tr>}
      {queue.map((s, i) => <tr key={s.id}><td className="font-mono font-bold text-brand">#{s.queuePosition ?? i + 1}{s.scheduledStartAt && new Date(s.scheduledStartAt).getTime() > Date.now() && <div className="text-xs font-sans text-slate-500">arrives {new Date(s.scheduledStartAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</div>}</td><td>{s.vehicleKind} #{s.vehicleId}<div className="text-xs text-slate-500">CS-{s.id}</div></td><td><Battery value={s.batteryLevel} /></td><td className="font-semibold tabular-nums">{s.priorityScore.toFixed(1)}</td><td><StatusBadge status={s.priorityLevel} /></td><td>{s.waitingMinutes} min</td><td><button disabled={busyId === s.id} className="btn btn-secondary btn-sm" onClick={() => onAdjust(s)}>Adjust</button></td></tr>)}
    </tbody></table></div>
    <div className="grid gap-3 md:grid-cols-2">{active.map(s => <div key={s.id} className="card flex items-center justify-between p-4"><div><div className="font-semibold">{s.vehicleKind} #{s.vehicleId} · CS-{s.id}</div><div className="hint mt-1">{s.chargingPointId ? `Charging point CP-${s.chargingPointId}` : "Port pending"} · {s.batteryLevel}% battery{s.userId == null && " · automatic request"}</div></div><div className="flex items-center gap-2"><SessionStatus status={s.status} />{s.status === "SCHEDULED" && s.userId == null && <button disabled={busyId === s.id} className="btn btn-primary btn-sm" onClick={() => onStart(s.id)}>Start</button>}</div></div>)}</div>
  </div>;
}
