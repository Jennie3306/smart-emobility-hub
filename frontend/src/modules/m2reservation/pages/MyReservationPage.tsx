import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { errorMessage, hubName, reservationApi, type Reservation, type ReservationState } from "../api/reservationApi";
import { CurrentTripCard } from "../../m3vehicle/components/CurrentTripCard";
import { Card, EmptyState, Notice, PageHeader, PageSkeleton, StatusBadge } from "@/shared/ui";

/** State labels and colours from table 5.2. */
const STATE_LABEL: Record<ReservationState, { text: string; className: string }> = {
  PENDING:    { text: "Pending",    className: "bg-state-idle" },
  CONFIRMED:  { text: "Held",  className: "bg-state-busy" },
  CHECKED_IN: { text: "In use",  className: "bg-brand" },
  COMPLETED:  { text: "Completed",    className: "bg-state-available" },
  CANCELLED:  { text: "Cancelled",        className: "bg-state-idle" },
  EXPIRED:    { text: "Expired",    className: "bg-state-fault" },
};

type Banner = { kind: "info" | "error"; text: string } | null;

/** S-M2-03 Current reservation. FR-M2-04, FR-M2-07, and the M3 current-trip card (FR-M3-03); reflects FR-M2-05 (Timer) and FR-M2-08 (vehicle failure). */
export default function MyReservationPage() {
  const [current, setCurrent] = useState<Reservation | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [banner, setBanner] = useState<Banner>(null);
  const [left, setLeft] = useState(0);
  const previous = useRef<Reservation | null>(null);
  const [hubs,setHubs]=useState<{id:number;name:string}[]>([]);
  const [returnHub,setReturnHub]=useState(1);
  const [returnBattery,setReturnBattery]=useState(75);
  useEffect(()=>{reservationApi.hubs().then(setHubs).catch(()=>setBanner({kind:"error",text:"Unable to load destination hubs."}));},[]);

  const load = useCallback(async () => {
    try {
      const next = await reservationApi.current();
      const prev = previous.current;
      if (prev && next && prev.id === next.id && prev.resourceId !== next.resourceId) {
        setBanner({ kind: "info", text: `Your vehicle failed and was replaced with ${next.vehicleCode}` });
      }
      if (prev && !next) {
        // The reservation ended without a user action -> ask the server why
        const closed = await reservationApi.get(prev.id);
        if (closed.state === "EXPIRED") setBanner({ kind: "error", text: "Reservation expired" });
        if (closed.cancelReason === "VEHICLE_FAILED") {
          setBanner({ kind: "error", text: "The reservation was cancelled because the vehicle failed" });
        }
      }
      previous.current = next;
      setCurrent(next);
      setLeft(next?.remainingSeconds ?? 0);
    } catch (e) {
      setBanner({ kind: "error", text: errorMessage(e) });
    } finally {
      setLoading(false);
    }
  }, []);

  // Load once, then refresh every 10 seconds to pick up Timer expiry / vehicle failure
  useEffect(() => {
    load();
    const timer = setInterval(load, 10_000);
    return () => clearInterval(timer);
  }, [load]);

  // Count down every second from the server's remainingSeconds
  useEffect(() => {
    const timer = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, []);

  async function act(action: () => Promise<Reservation>, success: string) {
    setBusy(true);
    setBanner(null);
    try {
      const updated = await action();
      previous.current = updated.state === "CONFIRMED" || updated.state === "CHECKED_IN" ? updated : null;
      setBanner({ kind: "info", text: success });
      await load();
    } catch (e) {
      setBanner({ kind: "error", text: errorMessage(e) });
      await load();
    } finally {
      setBusy(false);
    }
  }

  function cancel(id: number) {
    if (window.confirm("Cancel this reservation?")) act(() => reservationApi.cancel(id), "Reservation cancelled");
  }
  async function returnTrip() {
    if(!current) return;
    setBusy(true);setBanner(null);
    try {
      await reservationApi.returnVehicle(current.resourceId,returnHub,returnBattery);
      setBanner({kind:"info",text:"Vehicle returned. The M2 reservation and M3 trip are completed."});
      await load();
    } catch(e) {setBanner({kind:"error",text:errorMessage(e)});}
    finally {setBusy(false);}
  }

  if (loading) return <PageSkeleton blocks={2} />;

  const expired = current?.state === "CONFIRMED" && left === 0;
  const label = current ? STATE_LABEL[current.state] : null;

  return (
    <div className="space-y-6">
      <PageHeader module="M2 · M3" eyebrow="Reservation & trip" title="My reservation"
        description="Your 15-minute hold, the pick-up, the trip in progress and the return, on one page."
        actions={<Link to="/m2/search" className="btn btn-secondary">Find a vehicle</Link>} />

      {banner && <Notice kind={banner.kind === "error" ? "error" : "success"} onClose={() => setBanner(null)}>{banner.text}</Notice>}

      {!current ? (
        <EmptyState title="You have no active reservation."
          action={<Link to="/m2/search" className="btn btn-primary">Find a vehicle</Link>}>
          Reserve a vehicle and it is held for you for 15 minutes.
        </EmptyState>
      ) : (
        <div className="grid gap-6 xl:grid-cols-2">
          <Card title={<span className="flex items-center gap-3">Reservation #{current.id}
              {label && <StatusBadge status={expired ? "EXPIRED" : current.state}>{expired ? "Expired" : label.text}</StatusBadge>}</span>}
            subtitle={<>Vehicle <b className="text-slate-700">{current.vehicleCode ?? `#${current.resourceId}`}</b> at {hubName(current.hubId)}</>}>
            {current.state === "CONFIRMED" && (
              <div className="space-y-5">
                <div className="rounded-xl bg-brand-50 p-5 text-center">
                  <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Hold expires in</p>
                  <p className="mt-1 font-mono text-5xl font-bold tabular-nums text-brand">
                    {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}
                  </p>
                  <p className="mt-2 text-xs text-slate-500">Pick the vehicle up at the hub before the timer runs out.</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button className="btn btn-primary flex-1"
                          disabled={busy || expired}
                          onClick={() => act(() => reservationApi.checkIn(current.id), "Vehicle picked up")}>
                    Pick up vehicle
                  </button>
                  <button className="btn btn-danger flex-1"
                          disabled={busy || expired} onClick={() => cancel(current.id)}>
                    Cancel reservation
                  </button>
                </div>
              </div>
            )}
            {current.state === "CHECKED_IN" && (
              <CurrentTripCard hubLabel={hubName} pickupHubId={current.hubId ?? undefined} refreshKey={`${current.id}-${current.resourceId}`} />
            )}
          </Card>

          {current.state === "CHECKED_IN" ? (
            <Card title="Return your vehicle" subtitle="Returning needs a free space in the destination hub's parking pool (M4).">
              <div className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="label">Destination hub<select className="input" value={returnHub} onChange={e=>setReturnHub(Number(e.target.value))}>{hubs.map(h=><option key={h.id} value={h.id}>{h.name}</option>)}</select></label>
                  <label className="label">Battery on return (%)<input className="input" type="number" min={0} max={100} value={returnBattery} onChange={e=>setReturnBattery(Number(e.target.value))}/></label>
                </div>
                <p className="hint">If the hub is full, the system suggests hubs that still have room.</p>
                <button className="btn btn-primary w-full" disabled={busy||hubs.length===0||returnBattery<0||returnBattery>100} onClick={returnTrip}>{busy?"Returning…":"Return vehicle"}</button>
              </div>
            </Card>
          ) : (
            <Card title="How it works">
              <ol className="space-y-3 text-sm text-slate-600">
                {["Go to the hub and press Pick up vehicle before the hold expires.", "Ride. Your trip, its duration and the battery level appear here.", "Return at any hub with a free space and enter the battery level."].map((t, n) => (
                  <li key={n} className="flex gap-3"><span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand">{n + 1}</span>{t}</li>
                ))}
              </ol>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
