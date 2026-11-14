import { useEffect, useState } from 'react';
import { monitoringApi } from '../api/monitoringApi';
import type { Alert } from '../api/types';
import { Card, EmptyState, ErrorState, PageHeader, PageSkeleton, StatusBadge } from '@/shared/ui';

/** UC-M1-08 (FR-M1-10). Uu tien Could: bo duoc neu thieu thoi gian. */
export default function AlertHistoryPage() {
  const [alerts, setAlerts] = useState<Alert[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [days, setDays] = useState(7);
  const [type, setType] = useState('ALL');

  const load = () => {
    setError(null);
    const to = new Date();
    const from = new Date(to.getTime() - days * 86400000);
    monitoringApi.getAlertHistory({ from: from.toISOString(), to: to.toISOString() })
      .then(setAlerts)
      .catch(() => setError('Unable to load alert history'));
  };

  useEffect(load, [days]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!alerts) return <PageSkeleton blocks={2} />;

  const rows = type === 'ALL' ? alerts : alerts.filter(a => a.type === type);

  return (
    <div className="space-y-6">
      <PageHeader module="M1" eyebrow="Hub monitoring" title="Alert history"
        description="Alerts raised in the selected period with their final state, who acknowledged them and when they closed."
        actions={<>
          <select className="input mt-0 w-40" aria-label="Period" value={days}
                  onChange={e => setDays(Number(e.target.value))}>
            <option value={1}>Last 24 hours</option>
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
          </select>
          <select className="input mt-0 w-56" aria-label="Alert type" value={type}
                  onChange={e => setType(e.target.value)}>
            <option value="ALL">All types</option>
            <option value="CAPACITY">Parking capacity warning</option>
            <option value="LOW_VEHICLE">Low vehicle availability</option>
          </select>
        </>} />

      {rows.length === 0 ? (
        <EmptyState title="No alerts in the selected period">Choose a longer period or another alert type.</EmptyState>
      ) : (
        <Card flush>
          <div className="overflow-x-auto">
            <table className="table-ui">
              <thead>
                <tr>
                  <th>Raised</th><th>Hub</th><th>Type</th><th>Value</th>
                  <th>State</th><th>Acknowledged by</th><th>Closed</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(a => (
                  <tr key={a.id}>
                    <td className="whitespace-nowrap">{new Date(a.raisedAt).toLocaleString()}</td>
                    <td className="font-medium text-slate-900">{a.hubName}</td>
                    <td>{a.type === 'CAPACITY' ? 'Parking' : 'Vehicle shortage'}</td>
                    <td className="tabular-nums">{a.triggerValue}{a.type === 'CAPACITY' ? '%' : ''}</td>
                    <td><StatusBadge status={a.state} />{a.dismissReason && <div className="mt-1 text-xs text-slate-500">{a.dismissReason}</div>}</td>
                    <td>{a.acknowledgedBy ?? '—'}</td>
                    <td className="whitespace-nowrap">{a.closedAt ? new Date(a.closedAt).toLocaleString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
