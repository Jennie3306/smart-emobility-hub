import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Card, EmptyState, Notice, PageHeader, StatusBadge } from "@/shared/ui";
import {
  errorCode, errorMessage, hubName, reservationApi, VEHICLE_TYPE_LABEL,
  type HubSuggestion, type VehicleDetail, type VehicleSummary, type VehicleType,
} from "../api/reservationApi";

/** Battery bar colour from the design-system tokens (table 5.2). */
export const batteryColor = (p: number) =>
  p >= 50 ? "bg-state-available" : p >= 20 ? "bg-state-busy" : "bg-state-fault";

function BatteryBar({ level }: { level: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-full max-w-[9rem] overflow-hidden rounded-full bg-slate-200">
        <div className={`h-full rounded-full ${batteryColor(level)}`} style={{ width: `${level}%` }} />
      </div>
      <span className="text-xs font-medium tabular-nums text-slate-600">{level}%</span>
    </div>
  );
}

/** S-M2-01 Vehicle search + S-M2-02 Vehicle detail (modal). FR-M2-01, 02, 03, 10. */
export default function SearchVehiclePage() {
  const navigate = useNavigate();
  const [hubId, setHubId] = useState<number | undefined>(1);
  const [type, setType] = useState<VehicleType | undefined>();
  const [minBattery, setMinBattery] = useState(20);
  const [results, setResults] = useState<VehicleSummary[] | null>(null);
  const [suggestions, setSuggestions] = useState<HubSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<VehicleDetail | null>(null);
  const [hubs,setHubs]=useState<{id:number;name:string}[]>([]);
  useEffect(()=>{reservationApi.hubs().then(setHubs).catch(()=>setError("Unable to load live hub list."));},[]);

  async function search(forHub = hubId) {
    setLoading(true);
    setError(null);
    setSuggestions([]);
    try {
      const params = { hubId: forHub, type, minBattery };
      const list = await reservationApi.search(params);
      setResults(list);
      if (list.length === 0) setSuggestions(await reservationApi.alternatives(params));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  async function openDetail(id: number) {
    setError(null);
    try {
      setDetail(await reservationApi.detail(id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  function chooseHub(id: number) {
    setHubId(id);
    search(id);
  }

  return (
    <div className="space-y-6">
      <PageHeader module="M2" eyebrow="Find vehicles & reservation core" title="Find your next ride"
        description="Search rental vehicles by hub, type and minimum battery level. Only available vehicles are offered for reservation."
        actions={<Link to="/m2/my-reservation" className="btn btn-secondary">My reservation</Link>} />

      <Card title="Vehicle search" subtitle="Filters apply to the live fleet">
        <div className="grid items-end gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto]">
          <label className="label">Hub
            <select className="input" value={hubId ?? ""}
                    onChange={(e) => setHubId(e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">All</option>
              {hubs.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
            </select>
          </label>
          <label className="label">Vehicle type
            <select className="input" value={type ?? ""}
                    onChange={(e) => setType((e.target.value || undefined) as VehicleType | undefined)}>
              <option value="">All</option>
              <option value="E_BIKE">{VEHICLE_TYPE_LABEL.E_BIKE}</option>
              <option value="E_SCOOTER">{VEHICLE_TYPE_LABEL.E_SCOOTER}</option>
            </select>
          </label>
          <label className="label">Minimum battery (%)
            <input type="number" min={0} max={100} className="input"
                   value={minBattery} onChange={(e) => setMinBattery(Number(e.target.value))} />
          </label>
          <button className="btn btn-primary h-[38px] px-6" disabled={loading} onClick={() => search()}>
            Search
          </button>
        </div>
      </Card>

      {error && <Notice kind="error">{error}</Notice>}
      {loading && <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{[0, 1, 2].map(i => <div key={i} className="card h-36 animate-pulse bg-slate-100" />)}</div>}

      {!loading && results === null && (
        <EmptyState title="Start a search">Choose a hub and press Search to see the vehicles you can reserve.</EmptyState>
      )}

      {!loading && results?.length === 0 && (
        <EmptyState title="No matching vehicles available."
          action={suggestions.length > 0 && (
            <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
              <span className="text-slate-500">Try another hub:</span>
              {suggestions.map((s) => (
                <button key={s.hubId} className="btn btn-secondary btn-sm" onClick={() => chooseHub(s.hubId)}>
                  {hubName(s.hubId)} ({s.availableCount} vehicles)
                </button>
              ))}
            </div>
          )}>Lower the minimum battery or try another hub.</EmptyState>
      )}

      {!loading && !!results?.length && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {results.map((v) => (
            <article key={v.id} className="card flex flex-col gap-4 p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span aria-hidden className="grid h-11 w-11 place-items-center rounded-xl bg-brand-100 text-sm font-bold text-brand">{v.type === "E_BIKE" ? "EB" : "ES"}</span>
                  <div>
                    <p className="font-bold text-slate-900">{v.code}</p>
                    <p className="text-xs text-slate-500">{VEHICLE_TYPE_LABEL[v.type]} · {hubName(v.hubId)}</p>
                  </div>
                </div>
                <StatusBadge status="AVAILABLE">Available</StatusBadge>
              </div>
              <div>
                <p className="text-xs text-slate-500">Battery level</p>
                <div className="mt-1 flex items-end justify-between gap-3">
                  <span className="text-2xl font-bold tabular-nums text-slate-900">{v.batteryLevel}%</span>
                  <div className="flex-1"><BatteryBar level={v.batteryLevel} /></div>
                </div>
              </div>
              <button className="btn btn-primary mt-auto" onClick={() => openDetail(v.id)}>Details</button>
            </article>
          ))}
        </div>
      )}

      {detail && (
        <VehicleDetailModal
          vehicle={detail}
          onClose={() => setDetail(null)}
          onReserved={() => navigate("/m2/my-reservation")}
          onConflict={() => { setDetail(null); search(); }}
        />
      )}
    </div>
  );
}

/** S-M2-02 Vehicle detail. */
function VehicleDetailModal({ vehicle, onClose, onReserved, onConflict }: {
  vehicle: VehicleDetail;
  onClose: () => void;
  onReserved: () => void;
  onConflict: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState(vehicle.state !== "AVAILABLE");

  async function reserve() {
    setBusy(true);
    setError(null);
    try {
      await reservationApi.reserve(vehicle.id);
      onReserved();
    } catch (e) {
      const code = errorCode(e);
      if (code === "RESOURCE_CONFLICT") {
        alert(errorMessage(e));
        onConflict();
        return;
      }
      if (code === "VEHICLE_NOT_AVAILABLE") setBlocked(true);
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4" role="dialog" aria-modal="true">
      <div className="card w-full max-w-md space-y-4 p-6 shadow-xl">
        <div>
          <p className="eyebrow">Vehicle detail</p>
          <h2 className="mt-1 text-xl font-bold text-slate-900">Vehicle {vehicle.code}</h2>
        </div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-slate-500">Vehicle type</dt><dd>{VEHICLE_TYPE_LABEL[vehicle.type]}</dd>
          <dt className="text-slate-500">Battery</dt><dd><BatteryBar level={vehicle.batteryLevel} /></dd>
          <dt className="text-slate-500">Hub</dt><dd>{hubName(vehicle.hubId)}</dd>
          <dt className="text-slate-500">State</dt><dd><StatusBadge status={vehicle.state} /></dd>
        </dl>
        <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">The vehicle will be held for {vehicle.holdMinutes} minutes after booking.</p>
        {error && (
          <p className="text-sm text-red-700">
            {error}{" "}
            {error.includes("active reservation") && <Link to="/m2/my-reservation" className="link">View reservation</Link>}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button className="btn btn-secondary" onClick={onClose}>Close</button>
          <button className="btn btn-primary"
                  disabled={busy || blocked} onClick={reserve}>
            {busy ? "Booking…" : "Book vehicle"}
          </button>
        </div>
      </div>
    </div>
  );
}
