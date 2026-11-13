import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  incidentApi, incidentError, incidentSeverities, incidentStatuses, reasonsFor,
  type Incident, type IncidentAction, type IncidentResource, type IncidentSeverity,
} from "../api/incidentApi";

const actionNames: Record<IncidentAction, string> = { ASSIGN: "Phân công", START: "Bắt đầu xử lý", RESOLVE: "Đã sửa xong", REOPEN: "Mở lại", CLOSE: "Đóng" };
const resources: Record<IncidentResource, string> = { VEHICLE: "Xe", CHARGING_POINT: "Cổng sạc" };
const time = (value: string | null) => value ? new Date(value).toLocaleString() : "—";

function ReportForm({ onDone }: { onDone: (message: string) => void }) {
  const [type, setType] = useState<IncidentResource>("CHARGING_POINT");
  const [reason, setReason] = useState("OFFLINE");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    setSending(true); setError(null);
    try {
      const created = await incidentApi.report({
        hubId: Number(f.get("hubId")), resourceType: type, resourceId: Number(f.get("resourceId")),
        reason, severity: String(f.get("severity")) as IncidentSeverity,
        description: String(f.get("description") ?? "").trim() || undefined,
      });
      onDone(`Đã ghi nhận sự cố #${created.id}. Module sở hữu đã nhận thông báo hỏng và khóa tài nguyên.`);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setSending(false);
    }
  }

  return <section className="m6-panel" aria-labelledby="report-title">
    <h2 id="report-title">Báo cáo sự cố</h2>
    <form className="m6-form" onSubmit={submit}>
      <label htmlFor="inc-hub">Mã Hub</label><input id="inc-hub" name="hubId" type="number" min={1} required defaultValue={1} />
      <label htmlFor="inc-type">Loại tài nguyên</label>
      <select id="inc-type" value={type} onChange={e => { const t = e.target.value as IncidentResource; setType(t); setReason(reasonsFor[t][0]); }}>
        <option value="VEHICLE">Xe</option><option value="CHARGING_POINT">Cổng sạc</option>
      </select>
      <label htmlFor="inc-resource">Mã tài nguyên</label><input id="inc-resource" name="resourceId" type="number" min={1} required />
      <label htmlFor="inc-reason">Lý do</label>
      <select id="inc-reason" value={reason} onChange={e => setReason(e.target.value)}>
        {reasonsFor[type].map(r => <option key={r}>{r}</option>)}
      </select>
      <label htmlFor="inc-description">Mô tả{reason === "MANUAL_REPORT" ? " (bắt buộc)" : ""}</label>
      <textarea id="inc-description" name="description" rows={3} required={reason === "MANUAL_REPORT"} />
      <label htmlFor="inc-severity">Mức độ sự cố</label>
      <select id="inc-severity" name="severity" required defaultValue="">
        <option value="" disabled>Chọn mức độ</option>
        {incidentSeverities.map(value => <option key={value}>{value}</option>)}
      </select>
      <p className="m6-muted">Bắt buộc chọn, không có mức mặc định (BR-M6-10).</p>
      {error && <p className="m6-error" role="alert">{error}</p>}
      <button className="m6-primary" type="submit" disabled={sending}>{sending ? "Đang gửi…" : "Gửi báo cáo"}</button>
    </form>
  </section>;
}

export function IncidentPanel() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [status, setStatus] = useState("");
  const [resource, setResource] = useState("");
  const [severity, setSeverity] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [assignee, setAssignee] = useState("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reporting, setReporting] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setIncidents(await incidentApi.list({ status, resourceType: resource, severity }));
      setError(null);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setLoading(false);
    }
  }, [status, resource, severity]);
  useEffect(() => { load(); }, [load]);

  const selected = incidents.find(i => i.id === selectedId) ?? null;

  async function transition(action: IncidentAction) {
    if (!selected) return;
    setBusy(true); setMessage("");
    try {
      const updated = await incidentApi.transition(selected, action, action === "ASSIGN" ? assignee.trim() : undefined, note.trim() || undefined);
      setMessage(`Sự cố #${updated.id}: ${actionNames[action]} → ${updated.status}`);
      setAssignee(""); setNote(""); setError(null);
    } catch (e) {
      setError(incidentError(e));
    } finally {
      setBusy(false);
      await load();
    }
  }

  return <section aria-labelledby="incident-title">
    <div className="m6-toolbar">
      <h2 id="incident-title">Sự cố thiết bị</h2>
      <button className="m6-secondary" type="button" aria-expanded={reporting} onClick={() => setReporting(!reporting)}>{reporting ? "Ẩn form báo cáo" : "Báo cáo sự cố"}</button>
      <button className="m6-secondary" type="button" onClick={load}>Tải lại</button>
    </div>
    {reporting && <ReportForm onDone={text => { setMessage(text); setReporting(false); load(); }} />}
    {error && <p className="m6-error" role="alert">{error}</p>}
    <p role="status" aria-live="polite">{message}</p>
    <div className="m6-panel">
      <div className="m6-panel-title"><h2>Danh sách</h2><span>{incidents.length} sự cố</span></div>
      <div className="m6-filters">
        <label>Trạng thái<select value={status} onChange={e => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{incidentStatuses.map(v => <option key={v}>{v}</option>)}</select></label>
        <label>Tài nguyên<select value={resource} onChange={e => setResource(e.target.value)}><option value="">Tất cả tài nguyên</option>{Object.entries(resources).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label>Mức độ<select value={severity} onChange={e => setSeverity(e.target.value)}><option value="">Tất cả mức độ</option>{incidentSeverities.map(v => <option key={v}>{v}</option>)}</select></label>
      </div>
      {loading ? <p className="m6-empty">Đang tải…</p> : incidents.length === 0
        ? <p className="m6-empty" role="status">Không có sự cố phù hợp với bộ lọc.</p>
        : <div className="m6-table-wrap"><table>
          <thead><tr><th>Sự cố</th><th>Tài nguyên / Hub</th><th>Lý do</th><th>Mức độ</th><th>Người xử lý</th><th>Trạng thái</th></tr></thead>
          <tbody>{incidents.map(i => <tr key={i.id} className={i.id === selectedId ? "m6-selected" : ""}>
            <td><button type="button" className="m6-link" aria-label={`Xem sự cố ${i.id}`} onClick={() => { setSelectedId(i.id); setAssignee(""); setNote(""); setMessage(""); }}>#{i.id}</button>
              <small>{i.source === "DEVICE" ? "Từ thiết bị" : `Báo bởi ${i.reportedBy}`}</small></td>
            <td>{resources[i.resourceType]} #{i.resourceId}<small>Hub #{i.hubId}</small></td>
            <td>{i.reason}</td><td>{i.severity}</td><td>{i.assignee || "Chưa gán"}</td>
            <td><span className="m6-tag">{i.status}</span></td>
          </tr>)}</tbody>
        </table></div>}
    </div>
    {selected && <section className="m6-panel m6-detail" aria-labelledby="incident-detail-title">
      <div className="m6-panel-title"><h2 id="incident-detail-title">Chi tiết sự cố #{selected.id}</h2><span className="m6-tag">{selected.status}</span></div>
      {selected.description && <p>{selected.description}</p>}
      <dl className="m6-facts">
        <div><dt>Tài nguyên</dt><dd>{resources[selected.resourceType]} #{selected.resourceId} · Hub #{selected.hubId}</dd></div>
        <div><dt>Lý do / mức độ</dt><dd>{selected.reason} · {selected.severity}</dd></div>
        <div><dt>Báo cáo</dt><dd>{time(selected.reportedAt)} · {selected.reportedBy}</dd></div>
        <div><dt>Phân công / bắt đầu</dt><dd>{time(selected.assignedAt)} · {time(selected.startedAt)}</dd></div>
        <div><dt>Sửa xong / đóng</dt><dd>{time(selected.resolvedAt)} · {time(selected.closedAt)}</dd></div>
        <div><dt>Phiên bản</dt><dd>{selected.version}</dd></div>
        {selected.note && <div><dt>Ghi chú gần nhất</dt><dd>{selected.note}</dd></div>}
      </dl>
      {selected.allowedActions.includes("ASSIGN") && <label className="m6-assignee">Tên đăng nhập Operator xử lý
        <input value={assignee} onChange={e => setAssignee(e.target.value)} placeholder="vd. operator1" /></label>}
      {selected.allowedActions.length > 0 && <label className="m6-assignee">Ghi chú (tùy chọn)
        <input value={note} onChange={e => setNote(e.target.value)} /></label>}
      <div className="m6-toolbar">{selected.allowedActions.map(action => <button className="m6-secondary" type="button" key={action}
        disabled={busy || (action === "ASSIGN" && !assignee.trim())} onClick={() => transition(action)}>{actionNames[action]}</button>)}
        {selected.status === "CLOSED" && <p>Sự cố đã đóng; không còn thao tác nào (BR-M6-04).</p>}
      </div>
      <p className="m6-muted">Sửa xong sự cố không tự đưa thiết bị về hoạt động: thiết bị hoạt động lại khi có số đọc mới (vd. chạy NORMAL_TICK), và module sở hữu quyết định trạng thái.</p>
    </section>}
  </section>;
}
