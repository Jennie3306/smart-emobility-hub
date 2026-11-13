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
    <section aria-label="Current trip" className="rounded border p-4 space-y-2">
      <h2 className="font-semibold">Current trip</h2>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
        <div><dt className="text-slate-500">Vehicle</dt><dd className="font-medium">{trip.vehicleCode}</dd></div>
        <div>
          <dt className="text-slate-500">Started</dt>
          <dd>{started.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {duration(tripMinutes(trip.startTime, now))}</dd>
        </div>
        <div><dt className="text-slate-500">Location</dt><dd>{location}</dd></div>
        <div><dt className="text-slate-500">Battery</dt><dd>{trip.batteryLevel}%</dd></div>
      </dl>
    </section>
  );
}
