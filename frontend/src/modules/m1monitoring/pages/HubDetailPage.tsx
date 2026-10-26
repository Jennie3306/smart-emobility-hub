import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import type { HubDetail } from '../api/types';
import { UtilisationBar } from './UtilisationBar';

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
      .catch(() => setError('Khong tai duoc chi tiet hub'));
  };

  useEffect(load, [hubId]);

  if (error) return (
    <div className="p-6">
      <p className="mb-3">{error}</p>
      <button className="rounded border px-3 py-1" onClick={load}>Thu lai</button>
    </div>
  );
  if (!hub) return <div className="m-6 h-40 animate-pulse rounded border bg-gray-100" />;

  const vehicles = stateFilter === 'ALL'
    ? hub.vehicles
    : hub.vehicles.filter(v => v.state === stateFilter);

  return (
    <div className="p-6">
      <Link to="/hubs" className="text-sm underline">&larr; Danh sach hub</Link>
      <h1 className="mt-2 text-2xl font-bold">{hub.name}</h1>
      <p className="text-gray-500">{hub.location}</p>

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <Tile label="Xe kha dung" value={`${hub.availableVehicles}`} />
        <Tile label="Cho do trong"
              value={`${hub.utilisation.freeSlots}/${hub.utilisation.totalSlots}`} />
        <Tile label="Tru sac trong"
              value={`${hub.utilisation.freePoints}/${hub.utilisation.operationalPoints}`} />
      </div>
      <div className="mt-3"><UtilisationBar u={hub.utilisation} /></div>

      <h2 className="mt-6 font-semibold">Xe tai hub</h2>
      <select className="my-2 rounded border px-2 py-1 text-sm"
              value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
        <option value="ALL">Tat ca trang thai</option>
        {[...new Set(hub.vehicles.map(v => v.state))].map(s =>
          <option key={s} value={s}>{s}</option>)}
      </select>

      {vehicles.length === 0 ? (
        <p className="py-6 text-gray-500">Hub nay hien khong co xe</p>
      ) : (
        <table className="w-full text-sm">
          <thead className="border-b text-left">
            <tr><th>Ma xe</th><th>Loai</th><th>Pin</th><th>Trang thai</th></tr>
          </thead>
          <tbody>
            {vehicles.map(v => (
              <tr key={v.id} className="border-b">
                <td className="py-1">{v.code}</td>
                <td>{v.type}</td>
                <td>
                  <div className="h-2 w-16 rounded bg-gray-200">
                    <div className="h-2 rounded bg-green-500"
                         style={{ width: `${v.batteryLevel}%` }} />
                  </div>
                </td>
                <td>{v.state}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <details className="mt-6">
        <summary className="cursor-pointer font-semibold">
          Cho do ({hub.parkingSlots.length})
        </summary>
        <div className="mt-2 flex flex-wrap gap-1 text-xs">
          {hub.parkingSlots.map(s =>
            <span key={s.id} className="rounded border px-2 py-1">
              #{s.slotNo} {s.state}
            </span>)}
        </div>
      </details>

      <details className="mt-3">
        <summary className="cursor-pointer font-semibold">
          Tru sac ({hub.chargingPoints.length})
        </summary>
        <div className="mt-2 flex flex-wrap gap-1 text-xs">
          {hub.chargingPoints.map(p =>
            <span key={p.id} className="rounded border px-2 py-1">
              #{p.pointNo} {p.state}
            </span>)}
        </div>
      </details>
    </div>
  );
}

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded border p-3">
      <div className="text-xs uppercase text-gray-500">{label}</div>
      <div className="text-xl font-semibold">{value}</div>
    </div>
  );
}
