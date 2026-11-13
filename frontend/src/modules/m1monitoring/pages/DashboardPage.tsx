import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import { useHubStream } from '../api/useHubStream';
import type { Alert, Dashboard } from '../api/types';
import { UtilisationBar } from './UtilisationBar';
import { BookingRiskPanel } from '../../m8simulation/pages/BookingRiskPanel';
import { Card, ErrorState, PageHeader, PageSkeleton, StatCard, StatusBadge } from '@/shared/ui';

/** UC-M1-04. Man hinh quan trong nhat cua module, chi operator xem duoc. */
export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [flashing, setFlashing] = useState<Set<number>>(new Set());

  const load = useCallback(() => {
    setError(null);
    monitoringApi.getDashboard()
      .then(setData)
      .catch(() => setError('Unable to load dashboard'));
  }, []);

  useEffect(load, [load]);

  // Chi vá phan bi anh huong, khong tai lai trang (FR-M1-06)
  const { live, lastUpdate } = useHubStream(
    status => setData(d => {
      if (!d) return d;
      flash(status.hubId);
      return {
        ...d,
        hubs: d.hubs.map(h => h.id === status.hubId
          ? { ...h, availableVehicles: status.availableVehicles, utilisation: status.utilisation }
          : h),
      };
    }),
    alert => setData(d => d ? { ...d, openAlerts: mergeAlert(d.openAlerts, alert) } : d),
    load,
  );

  function flash(hubId: number) {
    setFlashing(s => new Set(s).add(hubId));
    window.setTimeout(() => setFlashing(s => {
      const n = new Set(s); n.delete(hubId); return n;
    }), 800);
  }

  const acknowledge = (id: number) =>
    monitoringApi.acknowledge(id).then(a =>
      setData(d => d ? { ...d, openAlerts: mergeAlert(d.openAlerts, a) } : d));

  const dismiss = (id: number) => {
    const reason = window.prompt('Reason for dismissing this alert?');
    if (!reason) return;
    monitoringApi.dismiss(id, reason).then(a =>
      setData(d => d ? { ...d, openAlerts: mergeAlert(d.openAlerts, a) } : d));
  };

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!data) return <PageSkeleton />;

  const fleet = Object.values(data.vehicleCountByState).reduce((a, b) => a + b, 0);
  const measured = data.hubs.map(h => h.utilisation.parkingPercent).filter((p): p is number => p !== null);
  const avgParking = measured.length ? Math.round(measured.reduce((a, b) => a + b, 0) / measured.length) : null;

  return (
    <div className="space-y-6">
      <PageHeader module="M1" eyebrow="Hub monitoring & operations" title="Network operations"
        description="A live view of vehicle availability, parking capacity and charging infrastructure across the campus network."
        actions={<>
          <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${live ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>
            <span className={`h-2 w-2 rounded-full ${live ? 'bg-emerald-500' : 'bg-slate-400'}`} />
            {live ? 'Live updates connected' : 'Disconnected; refreshing every 15 seconds'}
          </span>
          <button className="btn btn-secondary" onClick={load}>↻ Refresh data</button>
        </>} />

      {!live && (
        <div role="status" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Data may be stale — last updated {lastUpdate.toLocaleTimeString()}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Mobility hubs" value={data.totalHubs} detail="Across the campus network" icon="⌂" />
        <StatCard label="Available vehicles" value={data.vehicleCountByState.AVAILABLE ?? 0} detail={`of ${fleet} vehicles in the fleet`} icon="↗" tone="green" />
        <StatCard label="Parking utilisation" value={avgParking === null ? '—' : `${avgParking}%`} detail="Average over all hubs" icon="▦" tone="amber" />
        <StatCard label="Open alerts" value={data.openAlertCount} detail={data.openAlertCount ? 'Need operator attention' : 'Network is healthy'} icon="▲" tone={data.openAlertCount ? 'red' : 'slate'} />
      </div>

      <Card title="Fleet by state" subtitle="Vehicles counted by their current state (M3 owns the state)">
        <div className="flex flex-wrap gap-3">
          {Object.entries(data.vehicleCountByState).map(([state, count]) => (
            <div key={state} className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2">
              <StatusBadge status={state} />
              <span className="text-lg font-bold tabular-nums text-slate-900">{count}</span>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2" title="Hub utilisation" subtitle="Parking occupied + reserved over all spaces; free chargers over operational chargers"
          actions={<span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">● Live</span>} flush>
          <div className="overflow-x-auto">
            <table className="table-ui">
              <thead>
                <tr><th>Hub</th><th>Available vehicles</th><th>Free parking</th><th>Free chargers</th><th>Utilisation</th></tr>
              </thead>
              <tbody>
                {data.hubs.map(h => (
                  <tr key={h.id}
                      className={`${h.hasOpenAlert ? 'bg-red-50/60' : ''} ${flashing.has(h.id) ? '!bg-amber-100' : ''}`}>
                    <td>
                      <Link to={`/m1/hubs/${h.id}`} className="font-semibold text-slate-900 hover:text-brand hover:underline">{h.name}</Link>
                      <div className="text-xs text-slate-500">{h.location}</div>
                    </td>
                    <td className="tabular-nums">{h.availableVehicles}</td>
                    <td className="tabular-nums">
                      {h.utilisation.freeSlots}/{h.utilisation.totalSlots}
                    </td>
                    <td className="tabular-nums">
                      {h.utilisation.freePoints}/{h.utilisation.operationalPoints}
                    </td>
                    <td><UtilisationBar u={h.utilisation} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="px-5 py-3 text-xs text-slate-500">Rows tinted red have an open alert. Figures update when an owner module announces a change, without reloading the page.</p>
        </Card>

        <Card title="Open alerts" subtitle="Thresholds are evaluated every 30 seconds"
          actions={data.openAlerts.length > 0 && <span className="rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700">{data.openAlerts.length} open</span>}>
          {data.openAlerts.length === 0 ? (
            <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
              No open alerts
            </p>
          ) : (
            <ul className="space-y-3">
              {data.openAlerts.map(a => (
                <li key={a.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                  <div className="flex items-start gap-3">
                    <span aria-hidden className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${a.type === 'CAPACITY' ? 'bg-red-50 text-red-600' : 'bg-orange-50 text-accent'}`}>{a.type === 'CAPACITY' ? '▲' : '↘'}</span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-semibold text-slate-900">{a.type === 'CAPACITY' ? 'Parking capacity warning' : 'Low vehicle availability'}</span>
                        <StatusBadge status={a.state} />
                      </div>
                      <div className="mt-0.5 text-xs text-slate-500">
                        {a.hubName} · value {a.triggerValue}{a.type === 'CAPACITY' ? '%' : ' vehicles'} · {new Date(a.raisedAt).toLocaleTimeString()}
                      </div>
                      <div className="mt-2 flex gap-2">
                        {a.state === 'OPEN' && (
                          <button className="btn btn-primary btn-sm" onClick={() => acknowledge(a.id)}>Acknowledge</button>
                        )}
                        <button className="btn btn-secondary btn-sm" onClick={() => dismiss(a.id)}>Dismiss</button>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Link to="/m7/redistribution" className="link mt-4 inline-block text-sm">Review redistribution proposals →</Link>
        </Card>
      </div>

      <BookingRiskPanel />
    </div>
  );
}

/** Canh bao da dong thi bo khoi danh sach, con lai thi thay the hoac them moi. */
function mergeAlert(list: Alert[], incoming: Alert): Alert[] {
  const without = list.filter(a => a.id !== incoming.id);
  const stillOpen = incoming.state === 'OPEN' || incoming.state === 'ACKNOWLEDGED';
  return stillOpen ? [incoming, ...without] : without;
}
