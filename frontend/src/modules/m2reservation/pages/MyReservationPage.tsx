import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { errorMessage, hubName, reservationApi, type Reservation, type ReservationState } from "../api/reservationApi";

/** State labels and colours from table 5.2. */
const STATE_LABEL: Record<ReservationState, { text: string; className: string }> = {
  PENDING:    { text: "Đang xử lý",    className: "bg-state-idle" },
  CONFIRMED:  { text: "Đang giữ chỗ",  className: "bg-state-busy" },
  CHECKED_IN: { text: "Đang sử dụng",  className: "bg-brand" },
  COMPLETED:  { text: "Hoàn thành",    className: "bg-state-available" },
  CANCELLED:  { text: "Đã hủy",        className: "bg-state-idle" },
  EXPIRED:    { text: "Đã hết hạn",    className: "bg-state-fault" },
};

type Banner = { kind: "info" | "error"; text: string } | null;

/** S-M2-03 Current reservation. FR-M2-04, FR-M2-07; reflects FR-M2-05 (Timer) and FR-M2-08 (vehicle failure). */
export default function MyReservationPage() {
  const [current, setCurrent] = useState<Reservation | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [banner, setBanner] = useState<Banner>(null);
  const [left, setLeft] = useState(0);
  const previous = useRef<Reservation | null>(null);

  const load = useCallback(async () => {
    try {
      const next = await reservationApi.current();
      const prev = previous.current;
      if (prev && next && prev.id === next.id && prev.resourceId !== next.resourceId) {
        setBanner({ kind: "info", text: `Xe cũ gặp sự cố, đã đổi sang xe ${next.vehicleCode}` });
      }
      if (prev && !next) {
        // The reservation ended without a user action -> ask the server why
        const closed = await reservationApi.get(prev.id);
        if (closed.state === "EXPIRED") setBanner({ kind: "error", text: "Đặt chỗ đã hết hạn" });
        if (closed.cancelReason === "VEHICLE_FAILED") {
          setBanner({ kind: "error", text: "Đặt chỗ đã bị hủy do xe gặp sự cố" });
        }
      }
      previous.current = next;
      setCurrent(next);
      setLeft(next?.remainingSeconds ?? 0);
    } catch (e) {
      setBanner({ kind: "error", text: errorMessage(e) });
    } finally {
      setLoading(false);
    }
  }, []);

  // Load once, then refresh every 10 seconds to pick up Timer expiry / vehicle failure
  useEffect(() => {
    load();
    const timer = setInterval(load, 10_000);
    return () => clearInterval(timer);
  }, [load]);

  // Count down every second from the server's remainingSeconds
  useEffect(() => {
    const timer = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, []);

  async function act(action: () => Promise<Reservation>, success: string) {
    setBusy(true);
    setBanner(null);
    try {
      const updated = await action();
      previous.current = updated.state === "CONFIRMED" || updated.state === "CHECKED_IN" ? updated : null;
      setBanner({ kind: "info", text: success });
      await load();
    } catch (e) {
      setBanner({ kind: "error", text: errorMessage(e) });
      await load();
    } finally {
      setBusy(false);
    }
  }

  function cancel(id: number) {
    if (window.confirm("Hủy đặt xe này?")) act(() => reservationApi.cancel(id), "Đã hủy đặt xe");
  }

  if (loading) return <div className="h-32 bg-slate-200 animate-pulse rounded" />;

  const expired = current?.state === "CONFIRMED" && left === 0;
  const label = current ? STATE_LABEL[current.state] : null;

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-brand">Đặt chỗ hiện tại (M2)</h1>

      {banner && (
        <p className={`p-2 rounded text-sm ${banner.kind === "error" ? "bg-state-fault/15 text-state-fault" : "bg-state-busy/20"}`}>
          {banner.text}
        </p>
      )}

      {!current ? (
        <div className="bg-white p-4 rounded shadow space-y-2">
          <p>Bạn chưa có đặt chỗ nào đang hiệu lực.</p>
          <Link to="/m2/search" className="inline-block bg-brand text-white px-4 py-1.5 rounded">Tìm xe</Link>
        </div>
      ) : (
        <div className="bg-white p-6 rounded shadow space-y-3">
          <div className="flex items-center gap-3">
            <span className="font-medium">Mã đặt chỗ #{current.id}</span>
            {label && (
              <span className={`text-white text-xs px-2 py-0.5 rounded ${expired ? "bg-state-fault" : label.className}`}>
                {expired ? "Đã hết hạn" : label.text}
              </span>
            )}
          </div>
          <p>Xe <b>{current.vehicleCode ?? `#${current.resourceId}`}</b> tại {hubName(current.hubId)}</p>

          {current.state === "CONFIRMED" && (
            <>
              <p className="text-4xl font-mono text-brand">
                {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}
              </p>
              <div className="flex gap-2">
                <button className="bg-brand text-white px-4 py-1.5 rounded disabled:opacity-50"
                        disabled={busy || expired}
                        onClick={() => act(() => reservationApi.checkIn(current.id), "Đã xác nhận nhận xe")}>
                  Nhận xe
                </button>
                <button className="bg-state-fault text-white px-4 py-1.5 rounded disabled:opacity-50"
                        disabled={busy || expired} onClick={() => cancel(current.id)}>
                  Hủy đặt xe
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
