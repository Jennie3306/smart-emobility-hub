import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
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
      <div className="h-2 w-28 bg-slate-200 rounded">
        <div className={`h-2 rounded ${batteryColor(level)}`} style={{ width: `${level}%` }} />
      </div>
      <span className="text-xs">{level}%</span>
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
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-brand">Find & book a vehicle (M2)</h1>

      <div className="flex flex-wrap gap-4 items-end bg-white p-4 rounded shadow text-sm">
        <label className="flex flex-col gap-1">Hub
          <select className="border rounded p-1" value={hubId ?? ""}
                  onChange={(e) => setHubId(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">All</option>
            {hubs.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">Vehicle type
          <select className="border rounded p-1" value={type ?? ""}
                  onChange={(e) => setType((e.target.value || undefined) as VehicleType | undefined)}>
            <option value="">All</option>
            <option value="E_BIKE">{VEHICLE_TYPE_LABEL.E_BIKE}</option>
            <option value="E_SCOOTER">{VEHICLE_TYPE_LABEL.E_SCOOTER}</option>
          </select>
        </label>
        <label className="flex flex-col gap-1">Minimum battery (%)
          <input type="number" min={0} max={100} className="border rounded p-1 w-24"
                 value={minBattery} onChange={(e) => setMinBattery(Number(e.target.value))} />
        </label>
        <button className="bg-brand text-white px-4 py-1.5 rounded disabled:opacity-50"
                disabled={loading} onClick={() => search()}>
          Search
        </button>
        <Link to="/m2/my-reservation" className="ml-auto text-brand underline">My reservation</Link>
      </div>

      {error && <p className="text-state-fault">{error}</p>}
      {loading && <div className="h-24 bg-slate-200 animate-pulse rounded" />}

      {!loading && results?.length === 0 && (
        <div className="bg-white p-4 rounded shadow space-y-2">
          <p>No matching vehicles available.</p>
          {suggestions.length > 0 && (
            <div className="flex flex-wrap gap-3 text-sm">
              <span>Try another hub:</span>
              {suggestions.map((s) => (
                <button key={s.hubId} className="text-brand underline" onClick={() => chooseHub(s.hubId)}>
                  {hubName(s.hubId)} ({s.availableCount} xe)
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {!loading && !!results?.length && (
        <table className="w-full bg-white rounded shadow text-sm">
          <thead className="bg-slate-100 text-left">
            <tr><th className="p-2">Battery</th><th>Hub</th><th>Vehicle code</th><th>Type</th><th /></tr>
          </thead>
          <tbody>
            {results.map((v) => (
              <tr key={v.id} className="border-t">
                <td className="p-2"><BatteryBar level={v.batteryLevel} /></td>
                <td>{hubName(v.hubId)}</td>
                <td className="font-medium">{v.code}</td>
                <td>{VEHICLE_TYPE_LABEL[v.type]}</td>
                <td className="text-right pr-2">
                  <button className="text-brand underline" onClick={() => openDetail(v.id)}>Details</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow p-6 w-full max-w-md space-y-3">
        <h2 className="text-lg font-bold text-brand">Vehicle {vehicle.code}</h2>
        <dl className="grid grid-cols-2 gap-y-1 text-sm">
          <dt>Vehicle type</dt><dd>{VEHICLE_TYPE_LABEL[vehicle.type]}</dd>
          <dt>Battery</dt><dd><BatteryBar level={vehicle.batteryLevel} /></dd>
          <dt>Hub</dt><dd>{hubName(vehicle.hubId)}</dd>
          <dt>State</dt><dd>{vehicle.state}</dd>
        </dl>
        <p className="text-sm text-state-idle">The vehicle will be held for {vehicle.holdMinutes} minutes after booking.</p>
        {error && (
          <p className="text-state-fault text-sm">
            {error}{" "}
            {error.includes("active reservation") && <Link to="/m2/my-reservation" className="underline">View reservation</Link>}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button className="px-4 py-1.5 rounded border" onClick={onClose}>Close</button>
          <button className="px-4 py-1.5 rounded bg-brand text-white disabled:opacity-50"
                  disabled={busy || blocked} onClick={reserve}>
            {busy ? "Booking…" : "Book vehicle"}
          </button>
        </div>
      </div>
    </div>
  );
}
