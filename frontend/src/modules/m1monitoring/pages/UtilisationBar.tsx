import type { Utilisation } from '../api/types';

/**
 * Mau thanh utilisation dung chung nguong voi luat canh bao:
 * duoi 50% xanh, 50-79% vang, tu 80% do (BR-M1-03).
 */
export function UtilisationBar({ u }: { u: Utilisation }) {
  const p = u.parkingPercent;
  if (p === null) return <span className="text-gray-400 text-sm">khong co du lieu</span>;

  const color = p >= 80 ? 'bg-red-500' : p >= 50 ? 'bg-amber-500' : 'bg-green-500';
  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-28 rounded bg-gray-200">
        <div className={`h-2 rounded ${color}`} style={{ width: `${p}%` }} />
      </div>
      <span className="text-sm tabular-nums">{p}%</span>
      {u.dataAnomaly && <span title="Du lieu khong nhat quan">!</span>}
    </div>
  );
}
