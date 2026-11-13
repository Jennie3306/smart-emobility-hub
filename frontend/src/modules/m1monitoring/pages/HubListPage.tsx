import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import type { HubSummary } from '../api/types';
import { UtilisationBar } from './UtilisationBar';
import { EmptyState, ErrorState, PageHeader } from '@/shared/ui';

/** UC-M1-01 va UC-M1-03. Man hinh danh sach hub cho sinh vien. */
export default function HubListPage() {
  const [hubs, setHubs] = useState<HubSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hasVehicle, setHasVehicle] = useState(false);
  const [hasFreeSlot, setHasFreeSlot] = useState(false);

  const load = () => {
    setError(null);
    monitoringApi.getHubs({ hasVehicle, hasFreeSlot })
      .then(setHubs)
      .catch(() => setError('Unable to load hubs'));
  };

  useEffect(load, [hasVehicle, hasFreeSlot]);

  const filter = (label: string, checked: boolean, set: (v: boolean) => void) => (
    <label className={`inline-flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition-colors ${checked ? 'border-brand bg-brand-100 text-brand' : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'}`}>
      <input type="checkbox" className="accent-[#1F4E79]" checked={checked} onChange={e => set(e.target.checked)} /> {label}
    </label>
  );

  return (
    <div className="space-y-6">
      <PageHeader module="M1" eyebrow="Hub directory" title="Mobility hubs"
        description="Check before you set out which hub still has a vehicle available or a free parking space."
        actions={<>{filter('Has available vehicles', hasVehicle, setHasVehicle)}{filter('Has free parking', hasFreeSlot, setHasFreeSlot)}</>} />

      {error ? <ErrorState message={error} onRetry={load} />
        : !hubs ? (
          // State dang tai: the skeleton, khong dung spinner toan trang
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => <div key={i} className="card h-44 animate-pulse bg-slate-100" />)}
          </div>
        ) : hubs.length === 0 ? (
          <EmptyState title="No mobility hubs registered">Try clearing the filters above.</EmptyState>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {hubs.map(h => (
              <Link key={h.id} to={`/m1/hubs/${h.id}`}
                    className="card group block p-5 transition-shadow hover:shadow-md">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-lg font-semibold text-slate-900 group-hover:text-brand">{h.name}</div>
                    <div className="text-sm text-slate-500">{h.location}</div>
                  </div>
                  {h.hasOpenAlert && <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-semibold text-red-700">Alert</span>}
                </div>
                <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
                  <div className="rounded-lg bg-slate-50 p-2"><dt className="text-[11px] text-slate-500">Vehicles</dt><dd className="text-lg font-bold tabular-nums text-slate-900">{h.availableVehicles}</dd></div>
                  <div className="rounded-lg bg-slate-50 p-2"><dt className="text-[11px] text-slate-500">Free parking</dt><dd className="text-lg font-bold tabular-nums text-slate-900">{h.utilisation.freeSlots}<span className="text-xs font-medium text-slate-400">/{h.utilisation.totalSlots}</span></dd></div>
                  <div className="rounded-lg bg-slate-50 p-2"><dt className="text-[11px] text-slate-500">Free chargers</dt><dd className="text-lg font-bold tabular-nums text-slate-900">{h.utilisation.freePoints}<span className="text-xs font-medium text-slate-400">/{h.utilisation.operationalPoints}</span></dd></div>
                </dl>
                <div className="mt-4"><UtilisationBar u={h.utilisation} /></div>
              </Link>
            ))}
          </div>
        )}
    </div>
  );
}
