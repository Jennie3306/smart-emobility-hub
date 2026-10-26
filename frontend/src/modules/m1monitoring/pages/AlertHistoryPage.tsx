import { useEffect, useState } from 'react';
import { monitoringApi } from '../api/monitoringApi';
import type { Alert } from '../api/types';

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
      .catch(() => setError('Khong tai duoc lich su canh bao'));
  };

  useEffect(load, [days]);

  if (error) return (
    <div className="p-6">
      <p className="mb-3">{error}</p>
      <button className="rounded border px-3 py-1" onClick={load}>Thu lai</button>
    </div>
  );
  if (!alerts) return <div className="m-6 h-40 animate-pulse rounded border bg-gray-100" />;

  const rows = type === 'ALL' ? alerts : alerts.filter(a => a.type === type);

  return (
    <div className="p-6">
      <h1 className="mb-4 text-xl font-bold">Lich su canh bao</h1>

      <div className="mb-4 flex gap-3 text-sm">
        <select className="rounded border px-2 py-1" value={days}
                onChange={e => setDays(Number(e.target.value))}>
          <option value={1}>24 gio qua</option>
          <option value={7}>7 ngay qua</option>
          <option value={30}>30 ngay qua</option>
        </select>
        <select className="rounded border px-2 py-1" value={type}
                onChange={e => setType(e.target.value)}>
          <option value="ALL">Moi loai</option>
          <option value="CAPACITY">Gan het cho do</option>
          <option value="LOW_VEHICLE">Sap het xe</option>
        </select>
      </div>

      {rows.length === 0 ? (
        <p className="py-10 text-center text-gray-500">
          Khong co canh bao nao trong khoang thoi gian da chon
        </p>
      ) : (
        <table className="w-full text-sm">
          <thead className="border-b text-left">
            <tr>
              <th>Thoi diem phat</th><th>Hub</th><th>Loai</th><th>Chi so</th>
              <th>Trang thai</th><th>Nguoi xac nhan</th><th>Thoi diem dong</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(a => (
              <tr key={a.id} className="border-b">
                <td className="py-1">{new Date(a.raisedAt).toLocaleString()}</td>
                <td>{a.hubName}</td>
                <td>{a.type === 'CAPACITY' ? 'Cho do' : 'Thieu xe'}</td>
                <td className="tabular-nums">{a.triggerValue}</td>
                <td>{a.state}{a.dismissReason ? ` (${a.dismissReason})` : ''}</td>
                <td>{a.acknowledgedBy ?? '—'}</td>
                <td>{a.closedAt ? new Date(a.closedAt).toLocaleString() : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
