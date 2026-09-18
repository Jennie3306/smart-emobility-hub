export default function DashboardPage() {
  return (
    <div>
      <h1 className="text-xl font-bold text-brand mb-4">Dashboard van hanh (M1)</h1>
      <p className="text-slate-600">
        TV1 lam trang nay: danh sach Hub, muc su dung, canh bao qua tai, cap nhat realtime
        qua STOMP (xem <code>shared/ws/stomp.ts</code>).
      </p>
    </div>
  );
}
