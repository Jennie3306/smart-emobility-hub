import { useEffect,useState } from "react";
import { Link } from "react-router-dom";
import { simulationApi,type Monitor } from "../api/simulationApi";

/** M1 dashboard surface; the backend modules communicate through their own read contracts. */
export function BookingRiskPanel() {
  const [data,setData]=useState<Monitor>();
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  useEffect(()=>{const load=()=>simulationApi.status().then(d=>{setData(d);setError("");}).catch(()=>setError("Booking-volume monitor is unavailable."));load();const id=setInterval(load,30000);return()=>clearInterval(id);},[]);
  return <section className="card p-5 text-sm">
    <div className="mb-2 flex flex-wrap items-center gap-3"><div><p className="eyebrow">M8 · What-if</p><h2 className="text-[15px] font-semibold text-slate-900">Repeat recent booking volume</h2></div><Link className="link" to="/m8/simulation">Open What-if Studio →</Link><button disabled={busy} className="btn btn-secondary btn-sm ml-auto" onClick={async()=>{setBusy(true);try{setData(await simulationApi.check());setError("");}catch{setError("Booking-volume check failed.");}finally{setBusy(false);}}}>{busy?"Checking…":"Run check now"}</button></div>
    <p className="mb-2 text-xs text-slate-500">Accepted bookings in the last 30 minutes, repeated against current resources. Unrecorded demand and travel times are excluded.</p>
    {error&&<p role="alert" className="mb-2 text-red-700">{error}</p>}
    {data?.warnings.map((w,i)=><p key={i} className="mb-1 text-slate-600">{w}</p>)}
    {data?.forecasts&&<div className="flex flex-wrap gap-3">{data.forecasts.filter(f=>f.recentBookings>0).map(f=><span key={f.hubId} className={`rounded-lg border px-3 py-2 ${f.projectedFailed>0?"border-amber-200 bg-amber-50 text-amber-900":"border-slate-200 bg-slate-50"}`}>{f.hubName}: {f.recentBookings} bookings · {f.projectedFailed} projected unserved</span>)}</div>}
  </section>;
}
