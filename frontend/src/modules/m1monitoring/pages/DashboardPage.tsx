import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { monitoringApi } from '../api/monitoringApi';
import { useHubStream } from '../api/useHubStream';
import type { Alert, Dashboard } from '../api/types';
import { UtilisationBar } from './UtilisationBar';

/** UC-M1-04. Man hinh quan trong nhat cua module, chi operator xem duoc. */
export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [flashing, setFlashing] = useState<Set<number>>(new Set());

  const load = useCallback(() => {
    setError(null);
    monitoringApi.getDashboard()
      .then(setData)
      .catch(() => setError('Khong tai duoc dashboard'));
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
    const reason = window.prompt('Ly do bo qua canh bao?');
    if (!reason) return;
    monitoringApi.dismiss(id, reason).then(a =>
      setData(d => d ? { ...d, openAlerts: mergeAlert(d.openAlerts, a) } : d));
  };

  if (error) return (
    <div className="p-6">
      <p className="mb-3">{error}</p>
      <button className="rounded border px-3 py-1" onClick={load}>Thu lai</button>
    </div>
  );
  if (!data) return (
    <div className="space-y-3 p-6">
      {[16, 48, 32].map((h, i) =>
        <div key={i} className={`animate-pulse rounded border bg-gray-100 h-${h}`} />)}
    </div>
  );

  return (
    <div className="p-6">
      {!live && (
        <div className="mb-4 rounded border border-amber-400 bg-amber-50 p-3 text-sm">
          Du lieu co the da cu — cap nhat luc {lastUpdate.toLocaleTimeString()}
        </div>
      )}

      <div className="mb-4 flex items-center gap-2 text-sm">
        <span className={`h-2 w-2 rounded-full ${live ? 'bg-green-500' : 'bg-gray-400'}`} />
        {live ? 'Dang ket noi thoi gian thuc' : 'Mat ket noi, dang goi lai moi 15 giay'}
      </div>

      {/* Vung 1 - dai tom tat */}
      <div className="mb-6 flex flex-wrap gap-3">
        {Object.entries(data.vehicleCountByState).map(([state, count]) => (
          <div key={state} className="rounded border px-4 py-2">
            <div className="text-xs uppercase text-gray-500">{state}</div>
            <div className="text-xl font-semibold tabular-nums">{count}</div>
          </div>
        ))}
        <div className="rounded border px-4 py-2">
          <div className="text-xs uppercase text-gray-500">Tong hub</div>
          <div className="text-xl font-semibold">{data.totalHubs}</div>
        </div>
        <div className="rounded border px-4 py-2">
          <div className="text-xs uppercase text-gray-500">Canh bao dang mo</div>
          <div className="text-xl font-semibold">{data.openAlertCount}</div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Vung 2 - bang hub */}
        <div className="lg:col-span-2">
          <h2 className="mb-2 font-semibold">Hub</h2>
          <table className="w-full text-sm">
            <thead className="border-b text-left">
              <tr><th>Hub</th><th>Xe</th><th>Cho do</th><th>Tru sac</th><th>Utilisation</th></tr>
            </thead>
            <tbody>
              {data.hubs.map(h => (
                <tr key={h.id}
                    className={`border-b ${h.hasOpenAlert ? 'bg-red-50' : ''} ${flashing.has(h.id) ? 'bg-yellow-100' : ''}`}>
                  <td className="py-1">
                    <Link to={`/hubs/${h.id}`} className="underline">{h.name}</Link>
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

        {/* Vung 3 - canh bao dang mo */}
        <div>
          <h2 className="mb-2 font-semibold">Canh bao dang mo</h2>
          {data.openAlerts.length === 0 ? (
            <p className="rounded border p-4 text-sm text-gray-500">
              Khong co canh bao nao dang mo
            </p>
          ) : (
            <ul className="space-y-2">
              {data.openAlerts.map(a => (
                <li key={a.id} className="rounded border p-3 text-sm">
                  <div className="font-semibold">
                    {a.type === 'CAPACITY' ? 'Gan het cho do' : 'Sap het xe'} — {a.hubName}
                  </div>
                  <div className="text-gray-600">
                    Chi so {a.triggerValue}{a.type === 'CAPACITY' ? '%' : ' xe'} ·{' '}
                    {new Date(a.raisedAt).toLocaleTimeString()} · {a.state}
                  </div>
                  <div className="mt-2 flex gap-2">
                    {a.state === 'OPEN' && (
                      <button className="rounded border px-2 py-1"
                              onClick={() => acknowledge(a.id)}>Xac nhan</button>
                    )}
                    <button className="rounded border px-2 py-1"
                            onClick={() => dismiss(a.id)}>Bo qua</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

/** Canh bao da dong thi bo khoi danh sach, con lai thi thay the hoac them moi. */
function mergeAlert(list: Alert[], incoming: Alert): Alert[] {
  const without = list.filter(a => a.id !== incoming.id);
  const stillOpen = incoming.state === 'OPEN' || incoming.state === 'ACKNOWLEDGED';
  return stillOpen ? [incoming, ...without] : without;
}
