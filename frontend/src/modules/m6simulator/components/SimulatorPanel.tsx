import { useEffect, useRef, useState } from "react";
import { describeApiError, normalizeSeed, scenarios, simulatorApi, type Scenario, type SimulationRun, type SimulatorStatus } from "../api/simulatorApi";

const labels: Record<Scenario, string> = {
  NORMAL_TICK: "Cập nhật thiết bị thông thường",
  VEHICLE_FAILURE: "Lỗi thiết bị xe",
  CHARGER_FAILURE: "Lỗi cổng sạc",
};
const states = { IDLE: "Chưa có run", RUNNING: "Đang chạy", SUCCEEDED: "Hoàn tất", FAILED: "Thất bại" };
type Failure = ReturnType<typeof describeApiError>;

function ApiFailure({ failure }: { failure: Failure }) {
  return <div className="m6-error" role="alert">
    <strong>{failure.status ? `HTTP ${failure.status} · ` : ""}{failure.message}</strong>
    {failure.code && <p className="m6-mono">{failure.code}</p>}
    {failure.details && Object.keys(failure.details).length > 0 && <pre>{JSON.stringify(failure.details, null, 2)}</pre>}
    {(failure.status === 401 || failure.status === 403) && <a href="/login">Đăng nhập tài khoản Operator</a>}
  </div>;
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("vi-VN") : "—";
}

function RunReceipt({ run }: { run: SimulationRun }) {
  return <section className="m6-panel" aria-labelledby="run-result-title">
    <div className="m6-panel-title"><h2 id="run-result-title">Kết quả run gần nhất</h2>
      <span className={`m6-tag ${run.error ? "m6-tag-error" : ""}`}>{run.error ? "Thất bại" : run.completedAt ? "Hoàn tất" : "Đang chạy"}</span></div>
    <p className="m6-mono m6-run-id">{run.runId}</p>
    <dl className="m6-facts">
      <div><dt>Scenario</dt><dd>{run.scenario}</dd></div><div><dt>Seed</dt><dd className="m6-mono">{run.seed}</dd></div>
      <div><dt>Bắt đầu</dt><dd>{date(run.startedAt)}</dd></div><div><dt>Kết thúc</dt><dd>{date(run.completedAt)}</dd></div>
    </dl>
    <dl className="m6-counts">
      <div><dt>Readings</dt><dd>{run.readingCount}</dd></div><div><dt>Đã xử lý</dt><dd>{run.processedCount}</dd></div>
      <div><dt>Trùng inbound ID</dt><dd>{run.duplicateCount}</dd></div><div><dt>Bị từ chối</dt><dd>{run.rejectedCount}</dd></div>
    </dl>
    {run.error && <p className="m6-error" role="alert">Run thất bại: {run.error}</p>}
    <h3>Sự kiện do run này phát</h3>
    {run.outboundEvents.length === 0 ? <p className="m6-muted">Chưa có sự kiện trong kết quả run này.</p> :
      <ul className="m6-events">{run.outboundEvents.map(event => <li key={event.eventId}>
        <strong>{event.eventType}</strong><span>{event.resourceType} / {event.resourceId}</span>
        <code>{event.eventId}</code>
      </li>)}</ul>}
  </section>;
}

export function SimulatorPanel() {
  const [status, setStatus] = useState<SimulatorStatus | null>(null);
  const [statusError, setStatusError] = useState<Failure | null>(null);
  const [runError, setRunError] = useState<Failure | null>(null);
  const [seedError, setSeedError] = useState("");
  const [seed, setSeed] = useState("100");
  const [scenario, setScenario] = useState<Scenario>("NORMAL_TICK");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  const statusRequest = useRef<AbortController | null>(null);
  const mounted = useRef(false);

  async function loadStatus() {
    statusRequest.current?.abort();
    const request = new AbortController();
    statusRequest.current = request;
    setLoading(true);
    try {
      const result = await simulatorApi.status(request.signal);
      if (!request.signal.aborted && mounted.current) { setStatus(result); setStatusError(null); }
    } catch (error) {
      if (!request.signal.aborted && mounted.current) setStatusError(describeApiError(error));
    } finally {
      if (!request.signal.aborted && mounted.current) setLoading(false);
    }
  }

  useEffect(() => {
    mounted.current = true;
    void loadStatus();
    return () => { mounted.current = false; statusRequest.current?.abort(); };
  }, []);

  async function run(event: React.FormEvent) {
    event.preventDefault();
    if (inFlight.current || loading || !status || statusError || status.state === "RUNNING") return;
    try { normalizeSeed(seed); setSeedError(""); } catch (error) {
      setSeedError(error instanceof Error ? error.message : "Seed không hợp lệ."); return;
    }
    inFlight.current = true;
    setSubmitting(true); setRunError(null);
    try {
      const result = await simulatorApi.run(scenario, seed);
      if (!mounted.current) return;
      setStatus(previous => previous && ({ ...previous, state: result.error ? "FAILED" : "SUCCEEDED", latestRun: result }));
      await loadStatus();
    } catch (error) {
      if (!mounted.current) return;
      const failure = describeApiError(error);
      setRunError(failure);
      // Refresh state after a concurrent run or server failure; never retry POST.
      if (failure.status === 409 || !failure.status || failure.status >= 500) await loadStatus();
    } finally {
      inFlight.current = false;
      if (mounted.current) setSubmitting(false);
    }
  }

  const blocked = loading || submitting || !status || !!statusError || status.state === "RUNNING";
  return <div className="m6-grid">
    <section className="m6-panel" aria-labelledby="simulator-title">
      <div className="m6-panel-title"><h2 id="simulator-title">Điều khiển simulator</h2>
        <button className="m6-secondary" type="button" disabled={loading || submitting} onClick={() => void loadStatus()}>Làm mới trạng thái</button></div>
      <p className="m6-muted">Mỗi lần chạy gửi một scenario và seed tới backend. Scheduler chỉ hiển thị trạng thái.</p>
      <div className="m6-live" aria-live="polite">
        {loading && <p role="status">Đang tải trạng thái…</p>}
        {status && <><span className="m6-tag">{states[status.state]}</span><dl className="m6-facts">
          <div><dt>Scheduler</dt><dd>{status.schedulerEnabled ? "Đang bật" : "Đang tắt"}</dd></div>
          <div><dt>Chu kỳ hiện tại</dt><dd>{status.intervalMs === null ? "—" : `${status.intervalMs.toLocaleString("vi-VN")} ms`}</dd></div>
        </dl></>}
      </div>
      {statusError && <><ApiFailure failure={statusError} /><p className="m6-muted">Chưa xác nhận được trạng thái mới; dữ liệu còn hiển thị có thể đã cũ.</p></>}
      {status?.state === "RUNNING" && <p className="m6-notice">Simulator đang bận. Làm mới trạng thái để kiểm tra khi run kết thúc.</p>}
      <form onSubmit={run} className="m6-form">
        <label htmlFor="m6-scenario">Scenario</label>
        <select id="m6-scenario" value={scenario} disabled={submitting} onChange={e => setScenario(e.target.value as Scenario)}>
          {scenarios.map(value => <option key={value} value={value}>{labels[value]}</option>)}
        </select>
        <label htmlFor="m6-seed">Seed</label>
        <input id="m6-seed" type="text" inputMode="text" value={seed} disabled={submitting} spellCheck={false}
          aria-invalid={!!seedError} aria-describedby="m6-seed-help m6-seed-error" onChange={e => { setSeed(e.target.value); setSeedError(""); }} />
        <p id="m6-seed-help" className="m6-muted">Số nguyên Long; cùng seed tái tạo nội dung readings, mỗi run vẫn có ID mới.</p>
        <p id="m6-seed-error" className="m6-field-error" role={seedError ? "alert" : undefined}>{seedError}</p>
        <button className="m6-primary" type="submit" disabled={blocked}>{submitting ? "Đang chạy…" : "Chạy scenario"}</button>
      </form>
      {runError && <ApiFailure failure={runError} />}
    </section>
    {status?.latestRun ? <RunReceipt run={status.latestRun} /> : <section className="m6-panel m6-empty">
      <h2>Kết quả run</h2><p>{loading ? "Đang đọc dữ liệu backend…" : statusError ? "Kết quả chưa tải được." : "Chưa có run nào trong phiên backend này."}</p>
      <p className="m6-muted">Chọn scenario và seed để bắt đầu. Kết quả sẽ hiển thị counts và ID sự kiện backend trả về.</p>
    </section>}
  </div>;
}
