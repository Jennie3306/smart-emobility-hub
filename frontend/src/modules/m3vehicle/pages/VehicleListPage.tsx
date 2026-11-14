import { useEffect, useMemo, useState } from "react";
import { vehicleApi, type Vehicle } from "../api/vehicleApi";
import { HUBS } from "@/modules/m2reservation/api/reservationApi";
import { BatteryMeter, Card, PageHeader, PageSkeleton, StatCard, StatusBadge } from "@/shared/ui";

const hubLabel = (id: number | null) => id == null ? "On the road" : HUBS.find(h => h.id === id)?.name ?? `Hub #${id}`;

/** Operator view of the rental fleet (M3): every vehicle with its state, battery and hub. */
export default function VehicleListPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);
  const [state, setState] = useState("ALL");

  useEffect(() => {
    vehicleApi.list().then(setVehicles).finally(() => setLoading(false));
  }, []);

  const states = useMemo(() => [...new Set(vehicles.map(v => v.state))], [vehicles]);
  if (loading) return <PageSkeleton blocks={2} />;

  const count = (s: string) => vehicles.filter(v => v.state === s).length;
  const rows = state === "ALL" ? vehicles : vehicles.filter(v => v.state === state);

  return (
    <div className="space-y-6">
      <PageHeader module="M3" eyebrow="Rental lifecycle" title="Rental fleet"
        description="Every rental vehicle with its current state, battery level and hub. M3 owns these states; other modules change them only through events." />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Fleet size" value={vehicles.length} icon="↗" />
        <StatCard label="Available" value={count("AVAILABLE")} detail="Battery ≥ 20 % at a hub" icon="✓" tone="green" />
        <StatCard label="On a trip or moving" value={count("IN_USE") + count("IN_TRANSIT")} detail="IN_USE or IN_TRANSIT" icon="⇄" tone="slate" />
        <StatCard label="Need attention" value={count("LOW_BATTERY") + count("MAINTENANCE")} detail="Low battery or maintenance" icon="!" tone="orange" />
      </div>
      <Card title="Vehicles" flush actions={
        <select className="input mt-0 w-44" aria-label="Filter by state" value={state} onChange={e => setState(e.target.value)}>
          <option value="ALL">All states</option>{states.map(s => <option key={s}>{s}</option>)}
        </select>}>
        <div className="overflow-x-auto">
          <table className="table-ui">
            <thead>
              <tr><th>Vehicle code</th><th>Type</th><th>Battery</th><th>State</th><th>Hub</th></tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <tr key={v.id}>
                  <td className="font-semibold text-slate-900">{v.code}</td>
                  <td>{v.type === "E_BIKE" ? "Electric bicycle" : "Electric scooter"}</td>
                  <td><BatteryMeter level={v.batteryLevel} /></td>
                  <td><StatusBadge status={v.state} /></td>
                  <td>{hubLabel(v.hubId)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
