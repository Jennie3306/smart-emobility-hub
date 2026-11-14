import { useEffect, useState } from "react";
import { vehicleApi, type CurrentTrip } from "../api/vehicleApi";

interface Props {
  /** Turns a Hub id into a display name. */
  hubLabel: (hubId: number) => string;
  /** Hub of the reservation, shown as the pick-up point while the vehicle is out. */
  pickupHubId?: number;
  /** Change it to load the trip again (e.g. after the reservation refreshes). */
  refreshKey?: unknown;
}

/** Minutes since the trip started; never negative. */
export function tripMinutes(startTime: string, now: Date): number {
  return Math.max(0, Math.floor((now.getTime() - new Date(startTime).getTime()) / 60_000));
}

function duration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  return h > 0 ? `${h} h ${minutes % 60} min` : `${minutes} min`;
}

/** S-M3-03 Current trip (FR-M3-03): start time, where the vehicle is and its battery level. */
export function CurrentTripCard({ hubLabel, pickupHubId, refreshKey }: Props) {
  const [trip, setTrip] = useState<CurrentTrip | null>(null);
  const [error, setError] = useState(false);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    let alive = true;
    vehicleApi.currentTrip()
      .then((t) => { if (alive) { setTrip(t); setError(false); } })
      .catch(() => { if (alive) setError(true); });
    return () => { alive = false; };
  }, [refreshKey]);

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  if (error) return <p className="text-sm text-state-fault">Unable to load the current trip.</p>;
  if (!trip) return null;

  const location = trip.hubId != null
    ? hubLabel(trip.hubId)
    : pickupHubId != null ? `On the road (picked up at ${hubLabel(pickupHubId)})` : "On the road";
  const started = new Date(trip.startTime);

  return (
    <section aria-label="Current trip" className="space-y-3 rounded-xl border border-indigo-100 bg-indigo-50/40 p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">Current trip</h2>
        <span className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-semibold text-indigo-700 ring-1 ring-inset ring-indigo-200"><span className="h-1.5 w-1.5 rounded-full bg-current" />{trip.vehicleState}</span>
      </div>
      <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <div className="rounded-lg bg-white p-3"><dt className="text-xs text-slate-500">Vehicle</dt><dd className="mt-0.5 font-bold text-slate-900">{trip.vehicleCode}</dd></div>
        <div className="rounded-lg bg-white p-3">
          <dt className="text-xs text-slate-500">Started</dt>
          <dd className="mt-0.5 font-semibold text-slate-900">{started.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {duration(tripMinutes(trip.startTime, now))}</dd>
        </div>
        <div className="rounded-lg bg-white p-3"><dt className="text-xs text-slate-500">Location</dt><dd className="mt-0.5 font-semibold text-slate-900">{location}</dd></div>
        <div className="rounded-lg bg-white p-3"><dt className="text-xs text-slate-500">Battery</dt><dd className="mt-0.5 font-bold text-slate-900">{trip.batteryLevel}%</dd></div>
      </dl>
    </section>
  );
}
