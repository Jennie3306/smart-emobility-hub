import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import type { HubSummary } from '../api/types';
import { UtilisationBar } from './UtilisationBar';

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
      .catch(() => setError('Khong tai duoc danh sach hub'));
  };

  useEffect(load, [hasVehicle, hasFreeSlot]);

  if (error) return (
    <div className="p-6">
      <p className="mb-3">{error}</p>
      <button className="rounded border px-3 py-1" onClick={load}>Thu lai</button>
    </div>
  );

  // Trang thai dang tai: the skeleton, khong dung spinner toan trang
  if (!hubs) return (
    <div className="grid gap-3 p-6 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 6 }).map((_, i) =>
        <div key={i} className="h-28 animate-pulse rounded border bg-gray-100" />)}
    </div>
  );

  return (
    <div className="p-6">
      <div className="mb-4 flex gap-4 text-sm">
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={hasVehicle}
                 onChange={e => setHasVehicle(e.target.checked)} /> Con xe
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={hasFreeSlot}
                 onChange={e => setHasFreeSlot(e.target.checked)} /> Con cho trong
        </label>
      </div>

      {hubs.length === 0 ? (
        <p className="py-12 text-center text-gray-500">
          Chua co Mobility Hub nao duoc dang ky
        </p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {hubs.map(h => (
            <Link key={h.id} to={`/hubs/${h.id}`}
                  className="rounded border p-4 hover:bg-gray-50">
              <div className="text-lg font-semibold">{h.name}</div>
              <div className="text-sm text-gray-500">{h.location}</div>
              <div className="mt-2 text-sm">
                {h.availableVehicles} xe kha dung ·{' '}
                {h.utilisation.freeSlots}/{h.utilisation.totalSlots} cho trong ·{' '}
                {h.utilisation.freePoints}/{h.utilisation.operationalPoints} tru trong
              </div>
              <div className="mt-2"><UtilisationBar u={h.utilisation} /></div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
