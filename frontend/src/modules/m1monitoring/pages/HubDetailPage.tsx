import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import type { HubDetail } from '../api/types';
import { UtilisationBar } from './UtilisationBar';
import { BatteryMeter, Card, ErrorState, PageHeader, PageSkeleton, StatCard, StatusBadge } from '@/shared/ui';

/** UC-M1-02. */
export default function HubDetailPage() {
  const { hubId } = useParams();
  const [hub, setHub] = useState<HubDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [stateFilter, setStateFilter] = useState('ALL');

  const load = () => {
    setError(null);
    monitoringApi.getHub(Number(hubId))
      .then(setHub)
      .catch(() => setError('Unable to load hub details'));
  };

  useEffect(load, [hubId]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!hub) return <PageSkeleton />;

  const vehicles = stateFilter === 'ALL'
    ? hub.vehicles
    : hub.vehicles.filter(v => v.state === stateFilter);

  return (
    <div className="space-y-6">
      <Link to="/m1/hubs" className="link text-sm">&larr; Hub list</Link>
      <PageHeader module="M1" eyebrow="Hub detail" title={hub.name} description={hub.location}
        actions={<div className="w-56"><UtilisationBar u={hub.utilisation} /></div>} />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Available vehicles" value={hub.availableVehicles} icon="↗" tone="green" />
        <StatCard label="Free parking slots" value={`${hub.utilisation.freeSlots}/${hub.utilisation.totalSlots}`} icon="▦" tone="amber" />
        <StatCard label="Free chargers" value={`${hub.utilisation.freePoints}/${hub.utilisation.operationalPoints}`} icon="⚡" />
      </div>

      <Card title="Vehicles at this hub" flush actions={
        <select className="input mt-0 w-44" aria-label="Filter by state"
                value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
          <option value="ALL">All states</option>
          {[...new Set(hub.vehicles.map(v => v.state))].map(s =>
            <option key={s} value={s}>{s}</option>)}
        </select>}>
        {vehicles.length === 0 ? (
          <p className="px-5 pb-6 pt-2 text-sm text-slate-500">This hub has no vehicles right now.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-ui">
              <thead>
                <tr><th>Vehicle code</th><th>Type</th><th>Battery</th><th>State</th></tr>
              </thead>
              <tbody>
                {vehicles.map(v => (
                  <tr key={v.id}>
                    <td className="font-semibold text-slate-900">{v.code}</td>
                    <td>{v.type}</td>
                    <td><BatteryMeter level={v.batteryLevel} /></td>
                    <td><StatusBadge status={v.state} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title={`Parking (${hub.parkingSlots.length})`}>
          <div className="flex flex-wrap gap-2">
            {hub.parkingSlots.map(s => <StatusBadge key={s.id} status={s.state}>#{s.slotNo} {s.state}</StatusBadge>)}
          </div>
        </Card>
        <Card title={`Chargers (${hub.chargingPoints.length})`}>
          <div className="flex flex-wrap gap-2">
            {hub.chargingPoints.map(p => <StatusBadge key={p.id} status={p.state}>#{p.pointNo} {p.state}</StatusBadge>)}
          </div>
        </Card>
      </div>
    </div>
  );
}
