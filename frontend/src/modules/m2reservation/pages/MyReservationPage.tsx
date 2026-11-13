import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { errorMessage, hubName, reservationApi, type Reservation, type ReservationState } from "../api/reservationApi";

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

/** S-M2-03 Current reservation. FR-M2-04, FR-M2-07; reflects FR-M2-05 (Timer) and FR-M2-08 (vehicle failure). */
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

  if (loading) return <div className="h-32 bg-slate-200 animate-pulse rounded" />;

  const expired = current?.state === "CONFIRMED" && left === 0;
  const label = current ? STATE_LABEL[current.state] : null;

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-brand">My reservation (M2)</h1>

      {banner && (
        <p className={`p-2 rounded text-sm ${banner.kind === "error" ? "bg-state-fault/15 text-state-fault" : "bg-state-busy/20"}`}>
          {banner.text}
        </p>
      )}

      {!current ? (
        <div className="bg-white p-4 rounded shadow space-y-2">
          <p>You have no active reservation.</p>
          <Link to="/m2/search" className="inline-block bg-brand text-white px-4 py-1.5 rounded">Find a vehicle</Link>
        </div>
      ) : (
        <div className="bg-white p-6 rounded shadow space-y-3">
          <div className="flex items-center gap-3">
            <span className="font-medium">Reservation #{current.id}</span>
            {label && (
              <span className={`text-white text-xs px-2 py-0.5 rounded ${expired ? "bg-state-fault" : label.className}`}>
                {expired ? "Expired" : label.text}
              </span>
            )}
          </div>
          <p>Vehicle <b>{current.vehicleCode ?? `#${current.resourceId}`}</b> at {hubName(current.hubId)}</p>
          {current.state==="CHECKED_IN"&&<section className="space-y-3 rounded border p-4"><h2 className="font-semibold">Return your vehicle</h2><div className="flex flex-wrap gap-4"><label>Destination hub<select className="ml-2 rounded border p-2" value={returnHub} onChange={e=>setReturnHub(Number(e.target.value))}>{hubs.map(h=><option key={h.id} value={h.id}>{h.name}</option>)}</select></label><label>Battery on return (%)<input className="ml-2 w-24 rounded border p-2" type="number" min={0} max={100} value={returnBattery} onChange={e=>setReturnBattery(Number(e.target.value))}/></label></div><p className="text-xs text-slate-500">Returning needs a free space in the destination Hub's parking pool (M4). If the Hub is full, the system suggests Hubs that still have room.</p><button className="rounded bg-brand px-4 py-2 text-white disabled:opacity-50" disabled={busy||hubs.length===0||returnBattery<0||returnBattery>100} onClick={returnTrip}>{busy?"Returning…":"Return vehicle"}</button></section>}

          {current.state === "CONFIRMED" && (
            <>
              <p className="text-4xl font-mono text-brand">
                {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}
              </p>
              <div className="flex gap-2">
                <button className="bg-brand text-white px-4 py-1.5 rounded disabled:opacity-50"
                        disabled={busy || expired}
                        onClick={() => act(() => reservationApi.checkIn(current.id), "Vehicle picked up")}>
                  Pick up vehicle
                </button>
                <button className="bg-state-fault text-white px-4 py-1.5 rounded disabled:opacity-50"
                        disabled={busy || expired} onClick={() => cancel(current.id)}>
                  Cancel reservation
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
