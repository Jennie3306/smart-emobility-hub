import type { Utilisation } from '../api/types';

/**
 * Mau thanh utilisation dung chung nguong voi luat canh bao:
 * duoi 50% xanh, 50-79% vang, tu 80% do (BR-M1-03).
 */
export function UtilisationBar({ u }: { u: Utilisation }) {
  const p = u.parkingPercent;
  if (p === null) return <span className="text-sm text-slate-400">No data</span>;

  const color = p >= 80 ? 'bg-red-500' : p >= 50 ? 'bg-amber-500' : 'bg-emerald-500';
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-20 overflow-hidden rounded-full bg-slate-200">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${p}%` }} />
      </div>
      <span className="whitespace-nowrap text-xs font-medium tabular-nums text-slate-600">{p}%</span>
      {u.dataAnomaly && <span className="text-xs font-bold text-amber-600" title="Inconsistent data">!</span>}
    </div>
  );
}
