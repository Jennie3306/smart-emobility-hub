import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  currentRole, parkingApi, parkingErrorMessage,
  type Availability, type BookingStatus, type ParkingBooking, type ParkingHub, type ParkingSpace,
  type PersonalVehicle, type PersonalVehicleType, type SpaceStatus, type VehicleKind,
} from "../api/parkingApi";

/** Colours from the screen spec (Template_M4 section 5): green = free, amber = reserved, red = occupied / blocked. */
const SPACE_TONE: Record<SpaceStatus, string> = {
  FREE: "border-state-available bg-green-50 text-green-800 hover:bg-green-100",
  RESERVED: "border-state-busy bg-amber-50 text-amber-800",
  OCCUPIED: "border-state-fault bg-red-50 text-red-800",
  BLOCKED: "border-state-fault bg-red-100 text-red-900 line-through",
};

const BOOKING_TONE: Record<BookingStatus, string> = {
  ACTIVE: "bg-blue-100 text-blue-800",
  COMPLETED: "bg-green-100 text-green-800",
  NO_SHOW: "bg-red-100 text-red-800",
  CANCELLED: "bg-slate-100 text-slate-500",
};

const VEHICLE_TYPES: { value: PersonalVehicleType; label: string }[] = [
  { value: "E_MOTORBIKE", label: "Electric motorbike" },
  { value: "E_BIKE", label: "Electric bicycle" },
  { value: "E_SCOOTER", label: "Electric scooter" },
  { value: "E_CAR", label: "Electric car" },
];

type Tab = "book" | "tickets" | "vehicles";

function toLocalInput(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

/** Next quarter hour, so "now" bookings start on a tidy time. */
function nextQuarter(now = new Date()) {
  const d = new Date(now);
  d.setSeconds(0, 0);
  d.setMinutes(Math.ceil((d.getMinutes() + 1) / 15) * 15);
  return d;
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString([], { dateStyle: "short", timeStyle: "short" }) : "—";
}

function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}

function Countdown({ until, now }: { until: string; now: number }) {
  const left = Math.max(0, Math.floor((new Date(until).getTime() - now) / 1000));
  const minutes = Math.floor(left / 60);
  const seconds = left % 60;
  const tone = left === 0 ? "text-state-fault" : left < 300 ? "text-amber-700" : "text-slate-700";
  return <span className={`font-mono tabular-nums ${tone}`}>{String(minutes).padStart(2, "0")}:{String(seconds).padStart(2, "0")}</span>;
}

function Metric({ label, value, detail }: { label: string; value: string | number; detail: string }) {
  return <div className="rounded-lg border bg-white p-4 shadow-sm"><p className="text-xs text-slate-500">{label}</p><p className="mt-1 text-2xl font-bold text-brand">{value}</p><p className="mt-1 text-xs text-slate-500">{detail}</p></div>;
}

function SpaceGrid({ spaces, selectedId, onSelect, renderAction }: {
  spaces: ParkingSpace[];
  selectedId?: number | null;
  onSelect?: (space: ParkingSpace) => void;
  renderAction?: (space: ParkingSpace) => ReactNode;
}) {
  if (!spaces.length) return <div className="rounded-lg border border-dashed bg-white p-6 text-center text-sm text-slate-500">No spaces match this filter.</div>;
  return <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
    {spaces.map(space => {
      const selectable = onSelect != null && space.status === "FREE";
      const selected = selectedId === space.id;
      return <div key={space.id} className={`rounded border-2 p-2 text-center text-xs ${SPACE_TONE[space.status]} ${selected ? "ring-2 ring-brand ring-offset-1" : ""}`}>
        <button type="button" disabled={!selectable} onClick={() => { if (onSelect && space.status === "FREE") onSelect(space); }}
          className="w-full disabled:cursor-default" title={`${space.spaceCode} · ${space.type} · ${space.status}${space.blockedReason ? ` (${space.blockedReason})` : ""}${space.dockedVehicleId != null ? ` · rental vehicle #${space.dockedVehicleId}` : ""}`}>
          <div className="font-mono font-semibold">{space.spaceCode}{space.chargerPointId != null && " ⚡"}</div>
          <div className="mt-0.5 text-[10px] uppercase tracking-wide">{space.type} · {space.status}</div>
        </button>
        {renderAction?.(space)}
      </div>;
    })}
  </div>;
}

export default function ParkingPage() {
  const [role, setRole] = useState<"STUDENT" | "OPERATOR" | null>(null);
  const [hubs, setHubs] = useState<ParkingHub[]>([]);
  const [hubId, setHubId] = useState<number | null>(null);
  const [spaces, setSpaces] = useState<ParkingSpace[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadHubData = useCallback(async (selectedHub: number) => {
    const [hubList, spaceList] = await Promise.all([parkingApi.hubs(), parkingApi.spaces(selectedHub)]);
    setHubs(hubList);
    setSpaces(spaceList);
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const [userRole, hubList] = await Promise.all([currentRole(), parkingApi.hubs()]);
        if (!active) return;
        setRole(userRole);
        setHubs(hubList);
        const first = hubList[0]?.hubId ?? null;
        setHubId(first);
        if (first != null) setSpaces(await parkingApi.spaces(first));
      } catch (e) {
        if (active) setError(parkingErrorMessage(e));
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (hubId == null || loading) return;
    let active = true;
    const refresh = async () => {
      try { const list = await parkingApi.spaces(hubId); if (active) setSpaces(list); }
      catch (e) { if (active) setError(parkingErrorMessage(e)); }
    };
    refresh();
    const timer = window.setInterval(refresh, 15000);
    return () => { active = false; window.clearInterval(timer); };
  }, [hubId, loading]);

  const selectedHub = useMemo(() => hubs.find(h => h.hubId === hubId) ?? null, [hubs, hubId]);

  if (loading) return <div className="space-y-3"><div className="h-8 w-64 animate-pulse rounded bg-slate-200" /><div className="h-40 animate-pulse rounded bg-slate-200" /></div>;
  if (!role) return <p className="rounded border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error ?? "Unable to identify the signed-in role."}</p>;

  const shared = { hubs, hubId, setHubId, spaces, selectedHub, busy, setBusy, setError, setNotice, loadHubData };

  return <div className="space-y-5">
    <header>
      <p className="text-xs font-semibold uppercase tracking-widest text-brand">M4 · Parking</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-900">{role === "OPERATOR" ? "Parking spaces & bookings" : "Park at a Hub"}</h1>
      <p className="mt-1 max-w-3xl text-sm text-slate-600">Book a space for a time window, check in within 15 minutes of the start time, and check out when you leave. A booking that is not checked in within 15 minutes is released automatically.</p>
    </header>
    {error && <div role="alert" className="flex items-start justify-between gap-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"><span>{error}</span><button className="text-xs underline" onClick={() => setError(null)}>Dismiss</button></div>}
    {notice && <div role="status" className="flex items-start justify-between gap-4 rounded border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800"><span>{notice}</span><button className="text-xs underline" onClick={() => setNotice(null)}>Dismiss</button></div>}
    {role === "STUDENT" ? <StudentView {...shared} /> : <OperatorView {...shared} />}
  </div>;
}

interface SharedProps {
  hubs: ParkingHub[];
  hubId: number | null;
  setHubId: (id: number) => void;
  spaces: ParkingSpace[];
  selectedHub: ParkingHub | null;
  busy: boolean;
  setBusy: (busy: boolean) => void;
  setError: (message: string | null) => void;
  setNotice: (message: string | null) => void;
  loadHubData: (hubId: number) => Promise<void>;
}

// ------------------------------------------------------------------ Student

function StudentView(props: SharedProps) {
  const { setError } = props;
  const [tab, setTab] = useState<Tab>("book");
  const [vehicles, setVehicles] = useState<PersonalVehicle[]>([]);
  const [bookings, setBookings] = useState<ParkingBooking[]>([]);

  const loadMine = useCallback(async () => {
    const [v, b] = await Promise.all([parkingApi.myVehicles(), parkingApi.myBookings()]);
    setVehicles(v);
    setBookings(b);
  }, []);

  useEffect(() => {
    loadMine().catch(e => setError(parkingErrorMessage(e)));
    const timer = window.setInterval(() => { parkingApi.myBookings().then(setBookings).catch(() => undefined); }, 15000);
    return () => window.clearInterval(timer);
  }, [loadMine, setError]);

  const activeCount = bookings.filter(b => b.status === "ACTIVE").length;
  const tabs: { id: Tab; label: string }[] = [
    { id: "book", label: "Book a space" },
    { id: "tickets", label: `My tickets${activeCount ? ` (${activeCount})` : ""}` },
    { id: "vehicles", label: `My vehicles (${vehicles.length})` },
  ];

  return <div className="space-y-4">
    <nav className="flex gap-1 border-b">
      {tabs.map(t => <button key={t.id} onClick={() => setTab(t.id)}
        className={`-mb-px border-b-2 px-4 py-2 text-sm ${tab === t.id ? "border-brand font-semibold text-brand" : "border-transparent text-slate-600 hover:text-slate-900"}`}>{t.label}</button>)}
    </nav>
    {tab === "book" && <BookTab {...props} vehicles={vehicles} onBooked={async () => { await loadMine(); setTab("tickets"); }} />}
    {tab === "tickets" && <TicketsTab {...props} bookings={bookings} vehicles={vehicles} reload={loadMine} />}
    {tab === "vehicles" && <VehiclesTab {...props} vehicles={vehicles} reload={loadMine} />}
  </div>;
}

function BookTab({ hubs, hubId, setHubId, spaces, busy, setBusy, setError, setNotice, loadHubData, vehicles, onBooked }: SharedProps & {
  vehicles: PersonalVehicle[]; onBooked: () => Promise<void>;
}) {
  const [arrive, setArrive] = useState(() => toLocalInput(nextQuarter()));
  const [leave, setLeave] = useState(() => toLocalInput(new Date(nextQuarter().getTime() + 2 * 3600 * 1000)));
  const [kind, setKind] = useState<VehicleKind>("PERSONAL");
  const activeVehicles = vehicles.filter(v => v.status === "ACTIVE");
  const [vehicleId, setVehicleId] = useState<string>("");
  const [rentalId, setRentalId] = useState("");
  const [charge, setCharge] = useState(false);
  const [battery, setBattery] = useState("");
  const [spaceId, setSpaceId] = useState<number | null>(null);
  const [availability, setAvailability] = useState<Availability | null>(null);

  useEffect(() => {
    if (!vehicleId && activeVehicles[0]) setVehicleId(String(activeVehicles[0].id));
  }, [activeVehicles, vehicleId]);

  const vehicle = activeVehicles.find(v => String(v.id) === vehicleId) ?? null;
  const neededType = kind === "PERSONAL" && vehicle?.vehicleType === "E_CAR" ? "CAR" : "BIKE";
  const visibleSpaces = spaces.filter(s => s.type === neededType && (!charge || s.chargerPointId != null));

  useEffect(() => { setSpaceId(null); }, [hubId, neededType, charge]);

  useEffect(() => {
    if (hubId == null || !arrive || !leave) return;
    const start = new Date(arrive), end = new Date(leave);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) { setAvailability(null); return; }
    let active = true;
    const timer = window.setTimeout(() => {
      parkingApi.availability(hubId, start.toISOString(), end.toISOString())
        .then(a => { if (active) setAvailability(a); })
        .catch(() => { if (active) setAvailability(null); });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); };
  }, [hubId, arrive, leave]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (hubId == null) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      const booking = await parkingApi.book({
        hubId, startAt: new Date(arrive).toISOString(), endAt: new Date(leave).toISOString(), vehicleKind: kind,
        ...(kind === "PERSONAL" ? { personalVehicleId: Number(vehicleId) } : { rentalVehicleId: Number(rentalId) }),
        ...(spaceId != null ? { spaceId } : {}),
        requestCharging: charge,
        ...(battery.trim() ? { batteryLevel: Number(battery) } : {}),
      });
      setNotice(`Space ${booking.spaceCode} booked. Check in between ${formatTime(new Date(new Date(booking.slotStart).getTime() - 15 * 60000).toISOString())} and ${formatTime(booking.checkInDeadline)}.${booking.chargingRequested ? " Your charging request was sent to Smart charging (M5)." : ""}`);
      await loadHubData(hubId);
      await onBooked();
    } catch (e) {
      setError(parkingErrorMessage(e));
      await loadHubData(hubId).catch(() => undefined);
    } finally { setBusy(false); }
  }

  const noVehicle = kind === "PERSONAL" && !activeVehicles.length;

  return <div className="grid gap-4 xl:grid-cols-[minmax(0,420px)_1fr]">
    <form onSubmit={submit} className="space-y-4 rounded-lg border bg-white p-5 shadow-sm">
      <div><h2 className="font-semibold text-slate-900">Booking details</h2><p className="mt-1 text-xs text-slate-500">Bookings open 24 hours ahead and last from 30 minutes to 12 hours.</p></div>
      <label className="block text-sm">Hub<select className="mt-1 w-full rounded border p-2" value={hubId ?? ""} onChange={e => setHubId(Number(e.target.value))}>{hubs.map(h => <option key={h.hubId} value={h.hubId}>{h.hubName} · {h.free} free</option>)}</select></label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">Arrive<input required type="datetime-local" className="mt-1 w-full rounded border p-2" value={arrive} onChange={e => setArrive(e.target.value)} /></label>
        <label className="block text-sm">Leave<input required type="datetime-local" className="mt-1 w-full rounded border p-2" value={leave} onChange={e => setLeave(e.target.value)} /></label>
      </div>
      <fieldset className="text-sm"><legend className="mb-1">Vehicle</legend>
        <div className="flex gap-4">
          <label className="flex items-center gap-2"><input type="radio" checked={kind === "PERSONAL"} onChange={() => setKind("PERSONAL")} />My vehicle</label>
          <label className="flex items-center gap-2"><input type="radio" checked={kind === "RENTAL"} onChange={() => setKind("RENTAL")} />Rental vehicle</label>
        </div>
      </fieldset>
      {kind === "PERSONAL"
        ? (noVehicle
          ? <p className="rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">Register a vehicle under <strong>My vehicles</strong> first.</p>
          : <label className="block text-sm">Registered vehicle<select className="mt-1 w-full rounded border p-2" value={vehicleId} onChange={e => setVehicleId(e.target.value)}>{activeVehicles.map(v => <option key={v.id} value={v.id}>{v.licensePlate} · {v.model}</option>)}</select></label>)
        : <label className="block text-sm">Rental vehicle ID<input required min="1" type="number" className="mt-1 w-full rounded border p-2" value={rentalId} onChange={e => setRentalId(e.target.value)} /><span className="mt-1 block text-xs text-slate-500">The vehicle you reserved or picked up in Find a vehicle (M2).</span></label>}
      <div className="rounded border bg-slate-50 p-3 text-sm">
        <label className="flex items-center gap-2 font-medium"><input type="checkbox" checked={charge} onChange={e => setCharge(e.target.checked)} />Charge while parked ⚡</label>
        {charge && <label className="mt-2 block">Battery level now (%)<input min="0" max="89" type="number" className="mt-1 w-full rounded border p-2" value={battery} placeholder={vehicle?.batteryLevel != null && kind === "PERSONAL" ? `${vehicle.batteryLevel} (from your vehicle)` : ""} onChange={e => setBattery(e.target.value)} /></label>}
        {charge && <p className="mt-2 text-xs text-slate-500">Only spaces with a charging point. Smart charging (M5) queues your vehicle from your arrival time.</p>}
      </div>
      {availability && <p className={`text-xs ${availability.remaining > 0 ? "text-slate-600" : "font-semibold text-red-700"}`}>Capacity for this window: {availability.activeBookings} booked + {availability.dockedRentalVehicles} rental vehicles parked / {availability.capacity} · {availability.remaining} left</p>}
      <p className="text-xs text-slate-500">{spaceId != null ? <>Selected space <strong>{spaces.find(s => s.id === spaceId)?.spaceCode}</strong>. <button type="button" className="underline" onClick={() => setSpaceId(null)}>Let the system choose</button></> : "No space selected: the system chooses a suitable free space."}</p>
      <button disabled={busy || hubId == null || noVehicle || (availability != null && availability.remaining <= 0)} className="w-full rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{busy ? "Booking…" : charge ? "Book & charge" : "Book space"}</button>
    </form>
    <section className="space-y-3 rounded-lg border bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-2"><div><h2 className="font-semibold text-slate-900">{neededType === "CAR" ? "Car" : "Bike"} spaces{charge ? " with a charger" : ""}</h2><p className="text-xs text-slate-500">Status right now. Click a green space to choose it.</p></div>
        <div className="flex gap-3 text-xs text-slate-600"><span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-state-available" />Free</span><span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-state-busy" />Reserved</span><span><span className="mr-1 inline-block h-2 w-2 rounded-full bg-state-fault" />Occupied / blocked</span></div></div>
      <SpaceGrid spaces={visibleSpaces} selectedId={spaceId} onSelect={space => setSpaceId(space.id)} />
    </section>
  </div>;
}

function TicketsTab({ bookings, vehicles, hubs, busy, setBusy, setError, setNotice, reload, hubId, loadHubData }: SharedProps & {
  bookings: ParkingBooking[]; vehicles: PersonalVehicle[]; reload: () => Promise<void>;
}) {
  const now = useNow();
  const active = bookings.filter(b => b.status === "ACTIVE");
  const history = bookings.filter(b => b.status !== "ACTIVE");
  const hubName = (id: number) => hubs.find(h => h.hubId === id)?.hubName ?? `Hub #${id}`;
  const plate = (id: number | null) => vehicles.find(v => v.id === id)?.licensePlate;

  async function act(booking: ParkingBooking, action: "checkIn" | "checkOut" | "cancel") {
    setBusy(true); setError(null); setNotice(null);
    try {
      await parkingApi[action](booking.id);
      setNotice(action === "checkIn" ? `Checked in at ${booking.spaceCode}.` : action === "checkOut" ? `Checked out of ${booking.spaceCode}. The space is free again.` : "Booking cancelled.");
    } catch (e) { setError(parkingErrorMessage(e)); }
    finally {
      await reload().catch(() => undefined);
      if (hubId != null) await loadHubData(hubId).catch(() => undefined);
      setBusy(false);
    }
  }

  return <div className="space-y-5">
    {!active.length && <div className="rounded-lg border border-dashed bg-white p-8 text-center text-sm text-slate-500">No active tickets. Book a space to get one.</div>}
    <div className="grid gap-4 lg:grid-cols-2">
      {active.map(b => {
        const opensAt = new Date(b.slotStart).getTime() - 15 * 60000;
        const canCheckIn = !b.checkInTime && now >= opensAt && now < new Date(b.checkInDeadline).getTime();
        return <article key={b.id} className="rounded-lg border bg-white p-5 shadow-sm">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-500">{hubName(b.hubId)}</p>
              <p className="mt-1 text-2xl font-bold text-brand">{b.spaceCode}{b.hasCharger && " ⚡"}</p>
              <p className="mt-1 text-sm text-slate-600">{formatTime(b.slotStart)} → {formatTime(b.slotEnd)}</p>
              <p className="mt-1 text-xs text-slate-500">{b.vehicleKind === "PERSONAL" ? `Vehicle ${plate(b.personalVehicleId) ?? `#${b.personalVehicleId}`}` : "Rental vehicle"}{b.chargingRequested && " · charging requested"}</p>
            </div>
            <div className="rounded border-2 border-dashed border-brand/40 px-3 py-2 text-center"><p className="text-[10px] uppercase text-slate-500">Check-in PIN</p><p className="font-mono text-lg font-bold tracking-widest">{String(b.reservationId).padStart(6, "0")}</p></div>
          </div>
          <div className="mt-4 rounded bg-slate-50 p-3 text-sm">
            {b.checkInTime
              ? <>Checked in at {formatTime(b.checkInTime)}. Check out when you leave; otherwise you are checked out automatically 15 minutes after {formatTime(b.slotEnd)}. {b.spaceStatus === "BLOCKED" && <span className="text-red-700">The charger here failed; the space is blocked, but you can still check out.</span>}</>
              : now < opensAt
                ? <>Check-in opens at {formatTime(new Date(opensAt).toISOString())}.</>
                : <>Check in within <Countdown until={b.checkInDeadline} now={now} /> or the space is released.</>}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {!b.checkInTime && <button disabled={busy || !canCheckIn} onClick={() => act(b, "checkIn")} className="rounded bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Check in</button>}
            {b.checkInTime && <button disabled={busy} onClick={() => act(b, "checkOut")} className="rounded bg-brand px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">Check out</button>}
            {!b.checkInTime && <button disabled={busy} onClick={() => act(b, "cancel")} className="rounded border border-red-200 px-3 py-2 text-sm text-red-700 disabled:opacity-50">Cancel booking</button>}
          </div>
        </article>;
      })}
    </div>
    {history.length > 0 && <section className="space-y-2"><h2 className="font-semibold text-slate-900">History</h2>
      <div className="overflow-x-auto rounded-lg border bg-white"><table className="w-full text-sm"><thead className="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th className="p-3">Space</th><th className="p-3">Hub</th><th className="p-3">Window</th><th className="p-3">Check-in</th><th className="p-3">Check-out</th><th className="p-3">Result</th></tr></thead><tbody>
        {history.map(b => <tr key={b.id} className="border-t"><td className="p-3 font-mono">{b.spaceCode}</td><td className="p-3">{hubName(b.hubId)}</td><td className="p-3 text-xs">{formatTime(b.slotStart)} → {formatTime(b.slotEnd)}</td><td className="p-3 text-xs">{formatTime(b.checkInTime)}</td><td className="p-3 text-xs">{formatTime(b.checkOutTime)}</td><td className="p-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${BOOKING_TONE[b.status]}`}>{b.status.replace("_", "-")}</span></td></tr>)}
      </tbody></table></div>
    </section>}
  </div>;
}

function VehiclesTab({ vehicles, busy, setBusy, setError, setNotice, reload }: SharedProps & {
  vehicles: PersonalVehicle[]; reload: () => Promise<void>;
}) {
  const [editing, setEditing] = useState<PersonalVehicle | null>(null);
  const [plate, setPlate] = useState("");
  const [type, setType] = useState<PersonalVehicleType>("E_MOTORBIKE");
  const [model, setModel] = useState("");
  const [battery, setBattery] = useState("");

  function startEdit(v: PersonalVehicle | null) {
    setEditing(v);
    setPlate(v?.licensePlate ?? "");
    setType(v?.vehicleType ?? "E_MOTORBIKE");
    setModel(v?.model ?? "");
    setBattery(v?.batteryLevel != null ? String(v.batteryLevel) : "");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(null); setNotice(null);
    const body = { vehicleType: type, model, ...(battery.trim() ? { batteryLevel: Number(battery) } : {}) };
    try {
      if (editing) {
        await parkingApi.updateVehicle(editing.id, body);
        setNotice(`Vehicle ${editing.licensePlate} updated.`);
      } else {
        const created = await parkingApi.registerVehicle({ licensePlate: plate, ...body });
        setNotice(`Vehicle ${created.licensePlate} registered. You can now book a space for it.`);
      }
      startEdit(null);
      await reload();
    } catch (e) { setError(parkingErrorMessage(e)); }
    finally { setBusy(false); }
  }

  async function toggle(v: PersonalVehicle) {
    setBusy(true); setError(null); setNotice(null);
    try {
      if (v.status === "ACTIVE") await parkingApi.deactivateVehicle(v.id); else await parkingApi.activateVehicle(v.id);
      setNotice(v.status === "ACTIVE" ? `Vehicle ${v.licensePlate} removed from booking.` : `Vehicle ${v.licensePlate} is active again.`);
      await reload();
    } catch (e) { setError(parkingErrorMessage(e)); }
    finally { setBusy(false); }
  }

  return <div className="grid gap-4 xl:grid-cols-[1fr_minmax(0,380px)]">
    <div className="overflow-x-auto rounded-lg border bg-white"><table className="w-full text-sm"><thead className="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th className="p-3">License plate</th><th className="p-3">Type</th><th className="p-3">Model</th><th className="p-3">Battery</th><th className="p-3">Status</th><th className="p-3">Actions</th></tr></thead><tbody>
      {!vehicles.length && <tr><td colSpan={6} className="p-6 text-center text-slate-500">No vehicles yet. Add your first vehicle with the form.</td></tr>}
      {vehicles.map(v => <tr key={v.id} className="border-t">
        <td className="p-3 font-mono font-semibold">{v.licensePlate}</td>
        <td className="p-3">{VEHICLE_TYPES.find(t => t.value === v.vehicleType)?.label}</td>
        <td className="p-3">{v.model}</td>
        <td className="p-3">{v.batteryLevel != null ? `${v.batteryLevel}%` : "—"}</td>
        <td className="p-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${v.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-slate-100 text-slate-500"}`}>{v.status === "ACTIVE" ? "Active" : "Inactive"}</span></td>
        <td className="p-3"><div className="flex gap-2"><button disabled={busy} onClick={() => startEdit(v)} className="rounded border px-2 py-1 text-xs hover:bg-slate-50 disabled:opacity-50">Edit</button><button disabled={busy} onClick={() => toggle(v)} className={`rounded border px-2 py-1 text-xs disabled:opacity-50 ${v.status === "ACTIVE" ? "border-red-200 text-red-700" : ""}`}>{v.status === "ACTIVE" ? "Remove" : "Reactivate"}</button></div></td>
      </tr>)}
    </tbody></table></div>
    <form onSubmit={save} className="space-y-3 rounded-lg border bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between"><h2 className="font-semibold">{editing ? `Edit ${editing.licensePlate}` : "Add a vehicle"}</h2>{editing && <button type="button" className="text-sm text-slate-500" onClick={() => startEdit(null)}>Cancel</button>}</div>
      <label className="block text-sm">License plate<input required disabled={editing != null} className="mt-1 w-full rounded border p-2 uppercase disabled:bg-slate-100" value={plate} onChange={e => setPlate(e.target.value)} placeholder="59X1-123.45" /></label>
      <label className="block text-sm">Type<select className="mt-1 w-full rounded border p-2" value={type} onChange={e => setType(e.target.value as PersonalVehicleType)}>{VEHICLE_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select></label>
      <label className="block text-sm">Model<input required maxLength={60} className="mt-1 w-full rounded border p-2" value={model} onChange={e => setModel(e.target.value)} placeholder="VinFast Evo200" /></label>
      <label className="block text-sm">Battery level (%, optional)<input min="0" max="100" type="number" className="mt-1 w-full rounded border p-2" value={battery} onChange={e => setBattery(e.target.value)} /></label>
      <p className="text-xs text-slate-500">Cars park in CAR spaces; motorbikes, bicycles and scooters in BIKE spaces. The plate cannot be changed after registration.</p>
      <button disabled={busy} className="w-full rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{editing ? "Save changes" : "Register vehicle"}</button>
    </form>
  </div>;
}

// ------------------------------------------------------------------ Operator

function OperatorView({ hubs, hubId, setHubId, spaces, selectedHub, busy, setBusy, setError, setNotice, loadHubData }: SharedProps) {
  const [bookings, setBookings] = useState<ParkingBooking[]>([]);
  const [filter, setFilter] = useState<"ALL" | SpaceStatus>("ALL");

  const loadBookings = useCallback(async (id: number) => setBookings(await parkingApi.hubBookings(id)), []);

  useEffect(() => {
    if (hubId == null) return;
    loadBookings(hubId).catch(e => setError(parkingErrorMessage(e)));
    const timer = window.setInterval(() => { loadBookings(hubId).catch(() => undefined); }, 15000);
    return () => window.clearInterval(timer);
  }, [hubId, loadBookings, setError]);

  async function act(space: ParkingSpace, action: "block" | "unblock") {
    if (hubId == null) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      await parkingApi[action](space.id);
      setNotice(action === "block"
        ? `Space ${space.spaceCode} blocked.${space.status === "RESERVED" ? " Its booking was cancelled through M2." : ""}`
        : `Space ${space.spaceCode} is free again.`);
    } catch (e) { setError(parkingErrorMessage(e)); }
    finally {
      await loadHubData(hubId).catch(() => undefined);
      await loadBookings(hubId).catch(() => undefined);
      setBusy(false);
    }
  }

  const visible = filter === "ALL" ? spaces : spaces.filter(s => s.status === filter);
  const codeOf = (id: number) => spaces.find(s => s.id === id)?.spaceCode ?? `#${id}`;

  return <div className="space-y-5">
    <div className="flex flex-wrap items-center gap-3">
      <label className="text-sm">Hub <select className="ml-2 rounded border px-3 py-2" value={hubId ?? ""} onChange={e => setHubId(Number(e.target.value))}>{hubs.map(h => <option key={h.hubId} value={h.hubId}>{h.hubName}</option>)}</select></label>
      <button className="rounded border bg-white px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50" disabled={busy || hubId == null} onClick={() => hubId != null && Promise.all([loadHubData(hubId), loadBookings(hubId)]).catch(e => setError(parkingErrorMessage(e)))}>Refresh</button>
    </div>
    {selectedHub && <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <Metric label="Capacity" value={selectedHub.capacity} detail="Parking spaces at this Hub" />
      <Metric label="Free" value={selectedHub.free} detail={`${selectedHub.freeWithCharger} with a charger`} />
      <Metric label="Reserved" value={selectedHub.reserved} detail="Waiting for check-in" />
      <Metric label="Occupied" value={selectedHub.occupied} detail={`${selectedHub.occupied - selectedHub.dockedRental} checked in · ${selectedHub.dockedRental} rental vehicles`} />
      <Metric label="Blocked" value={selectedHub.blocked} detail="Incident or maintenance" />
    </div>}
    <section className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-2"><div><h2 className="font-semibold text-slate-900">Spaces</h2><p className="text-xs text-slate-500">Blocking a reserved space cancels its booking (BR-M4-06). A space blocked because its charger failed reopens when M5 reports the charger back in service. Rental vehicles standing at the Hub occupy spaces too (shared pool, BR-M4-08).</p></div>
        <select className="rounded border px-3 py-2 text-sm" value={filter} onChange={e => setFilter(e.target.value as "ALL" | SpaceStatus)}><option value="ALL">All statuses</option><option value="FREE">Free</option><option value="RESERVED">Reserved</option><option value="OCCUPIED">Occupied</option><option value="BLOCKED">Blocked</option></select></div>
      <SpaceGrid spaces={visible} renderAction={space => space.status === "BLOCKED"
        ? <button disabled={busy} onClick={() => act(space, "unblock")} className="mt-1 w-full rounded border border-slate-300 bg-white px-1 py-0.5 text-[10px] text-slate-700 no-underline disabled:opacity-50">Unblock</button>
        : <button disabled={busy} onClick={() => act(space, "block")} className="mt-1 w-full rounded border border-red-200 bg-white px-1 py-0.5 text-[10px] text-red-700 disabled:opacity-50">Block</button>} />
    </section>
    <section className="space-y-2"><h2 className="font-semibold text-slate-900">Recent bookings</h2>
      <div className="overflow-x-auto rounded-lg border bg-white"><table className="w-full text-sm"><thead className="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th className="p-3">Booking</th><th className="p-3">Space</th><th className="p-3">Vehicle</th><th className="p-3">Window</th><th className="p-3">Check-in deadline</th><th className="p-3">Checked in</th><th className="p-3">Status</th></tr></thead><tbody>
        {!bookings.length && <tr><td colSpan={7} className="p-6 text-center text-slate-500">No bookings at this Hub yet.</td></tr>}
        {bookings.map(b => <tr key={b.id} className="border-t"><td className="p-3 font-mono">PB-{b.id}<div className="text-xs text-slate-500">R-{b.reservationId}</div></td><td className="p-3 font-mono">{b.spaceCode ?? codeOf(b.spaceId)}{b.hasCharger && " ⚡"}</td><td className="p-3 text-xs">{b.vehicleKind}{b.chargingRequested && " · charging"}</td><td className="p-3 text-xs">{formatTime(b.slotStart)} → {formatTime(b.slotEnd)}</td><td className="p-3 text-xs">{formatTime(b.checkInDeadline)}</td><td className="p-3 text-xs">{formatTime(b.checkInTime)}</td><td className="p-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${BOOKING_TONE[b.status]}`}>{b.status.replace("_", "-")}</span></td></tr>)}
      </tbody></table></div>
    </section>
  </div>;
}
