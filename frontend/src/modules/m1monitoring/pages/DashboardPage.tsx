export default function DashboardPage() {
  return (
    <div>
      <h1 className="text-xl font-bold text-brand mb-4">Operations dashboard (M1)</h1>
      <p className="text-slate-600">
        M1 dashboard is pending: hub overview, utilization, overload alerts and real-time updates
        using STOMP (see <code>shared/ws/stomp.ts</code>).
      </p>
    </div>
  );
}
