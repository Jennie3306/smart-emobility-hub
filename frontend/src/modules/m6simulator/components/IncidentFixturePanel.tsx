import { useState, type FormEvent } from "react";
import { allowedFixtureActions, createIncidentFixtures, incidentSeverities, incidentStatuses, transitionFixture, type FixtureResource, type IncidentAction } from "../fixtures/incidents";

const actionNames: Record<IncidentAction, string> = { ASSIGN: "Gán người mẫu", START: "Bắt đầu mẫu", RESOLVE: "Resolve mẫu", REOPEN: "Reopen mẫu", CLOSE: "Close mẫu" };
const resources: Record<FixtureResource, string> = { VEHICLE: "Xe", CHARGING_POINT: "Cổng sạc" };

function ReportDraft() {
  const [preview, setPreview] = useState<Record<string, string> | null>(null);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setPreview(Object.fromEntries([...values.entries()].map(([key, value]) => [key, String(value)])));
  }
  return <section className="m6-panel">
    <h2>Bản mẫu báo cáo</h2>
    <p className="m6-muted">Chỉ xem bản nháp, chưa gửi hoặc thêm Incident. ID bên dưới là dữ liệu nhập mẫu.</p>
    <form className="m6-form" onSubmit={submit} onChange={() => setPreview(null)}>
      <label htmlFor="fixture-hub">Hub mẫu</label><select id="fixture-hub" name="hub"><option>Hub Metro (mẫu)</option><option>Hub KTX (mẫu)</option></select>
      <label htmlFor="fixture-type">Loại tài nguyên</label><select id="fixture-type" name="resourceType"><option value="VEHICLE">Xe</option><option value="CHARGING_POINT">Cổng sạc</option></select>
      <label htmlFor="fixture-resource">Mã tài nguyên mẫu</label><input id="fixture-resource" name="resourceId" required />
      <label htmlFor="fixture-reason">Reason code</label><input id="fixture-reason" name="reasonCode" value="MANUAL_REPORT" readOnly />
      <label htmlFor="fixture-description">Mô tả</label><textarea id="fixture-description" name="description" required rows={3} />
      <label htmlFor="fixture-severity">Severity báo cáo</label>
      <select id="fixture-severity" name="severity" required defaultValue="">
        <option value="" disabled>Chọn severity</option>
        {incidentSeverities.map(value => <option key={value}>{value}</option>)}
      </select>
      <p className="m6-muted">Bắt buộc chọn; không có mức mặc định. Severity không đổi luồng xử lý hoặc tự gán người.</p>
      <p className="m6-notice">Quy tắc Incident trùng đã chốt; bản nháp này chưa kiểm tra trùng.</p>
      <button className="m6-secondary" type="submit">Xem bản nháp</button>
    </form>
    {preview && <div className="m6-draft" role="status"><h3>Bản nháp chưa gửi</h3>
      <p>{preview.resourceType} / {preview.resourceId} · {preview.hub}</p><p>{preview.reasonCode}</p><p>{preview.description}</p>
      <p>Severity: {preview.severity}</p>
      <p className="m6-muted">Chưa xác minh tài nguyên hoặc Incident trùng; không có request nghiệp vụ.</p></div>}
  </section>;
}

export function IncidentFixturePanel() {
  const [incidents, setIncidents] = useState(createIncidentFixtures);
  const [selectedId, setSelectedId] = useState("FIX-001");
  const [status, setStatus] = useState("");
  const [resource, setResource] = useState("");
  const [severity, setSeverity] = useState("");
  const [query, setQuery] = useState("");
  const [assignee, setAssignee] = useState("");
  const [message, setMessage] = useState("");
  const [draft, setDraft] = useState(false);
  const selected = incidents.find(incident => incident.id === selectedId)!;
  const visible = incidents.filter(incident => (!status || incident.status === status)
    && (!resource || incident.resourceType === resource)
    && (!severity || incident.severity === severity)
    && `${incident.id} ${incident.resourceId} ${incident.hub}`.toLowerCase().includes(query.trim().toLowerCase()));

  function transition(action: IncidentAction) {
    setIncidents(previous => previous.map(incident => incident.id === selectedId ? transitionFixture(incident, action, assignee) : incident));
    setMessage(`${selectedId}: ${actionNames[action]} đã đổi dữ liệu mẫu trong trang.`);
  }
  function reset() {
    setIncidents(createIncidentFixtures()); setSelectedId("FIX-001"); setStatus(""); setResource(""); setSeverity(""); setQuery(""); setAssignee(""); setMessage(""); setDraft(false);
  }
  return <section aria-labelledby="incident-fixture-title">
    <div className="m6-notice"><h2 id="incident-fixture-title">Incident · Dữ liệu fixture</h2>
      <p>Chưa kết nối nghiệp vụ thật. Thao tác chỉ thay dữ liệu mẫu trong trang, không lưu database hay phát event. Tải lại trang sẽ đặt lại fixture.</p>
      <p>Severity theo quyết định TV2: LOW, MEDIUM, HIGH, CRITICAL. Fixture chưa thực thi chính sách Incident trùng hoặc thay đổi health.</p></div>
    <div className="m6-toolbar">
      <button className="m6-secondary" type="button" aria-expanded={draft} onClick={() => setDraft(!draft)}>{draft ? "Ẩn bản mẫu báo cáo" : "Mở bản mẫu báo cáo"}</button>
      <button className="m6-secondary" type="button" onClick={reset}>Đặt lại fixture</button>
    </div>
    {draft && <ReportDraft />}
    <div className="m6-panel">
      <div className="m6-panel-title"><h2>Danh sách mẫu</h2><span>{visible.length} / {incidents.length} bản mẫu</span></div>
      <div className="m6-filters">
        <label>Trạng thái<select value={status} onChange={e => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{incidentStatuses.map(value => <option key={value}>{value}</option>)}</select></label>
        <label>Tài nguyên<select value={resource} onChange={e => setResource(e.target.value)}><option value="">Tất cả tài nguyên</option>{Object.entries(resources).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>Severity<select value={severity} onChange={e => setSeverity(e.target.value)}><option value="">Tất cả severity</option>{incidentSeverities.map(value => <option key={value}>{value}</option>)}</select></label>
        <label>Tìm trong fixture<input value={query} onChange={e => setQuery(e.target.value)} placeholder="Mã fixture, tài nguyên hoặc Hub" /></label>
      </div>
      <div className="m6-table-wrap"><table>
        <caption className="sr-only">Incident fixture, chưa kết nối backend</caption>
        <thead><tr><th>Incident</th><th>Tài nguyên / Hub</th><th>Severity</th><th>Người xử lý mẫu</th><th>Trạng thái</th></tr></thead>
        <tbody>{visible.map(incident => <tr key={incident.id} className={incident.id === selectedId ? "m6-selected" : ""}>
          <td><button type="button" className="m6-link" aria-label={`Xem ${incident.id}`} onClick={() => { setSelectedId(incident.id); setAssignee(""); setMessage(""); }}>{incident.id}</button></td>
          <td>{resources[incident.resourceType]} / {incident.resourceId}<small>{incident.hub}</small></td>
          <td>{incident.severity}</td><td>{incident.assignee || "Chưa gán"}</td><td><span className="m6-tag">{incident.status}</span></td>
        </tr>)}</tbody>
      </table></div>
      {visible.length === 0 && <p className="m6-empty" role="status">Không có fixture phù hợp với bộ lọc.</p>}
    </div>
    <section className="m6-panel m6-detail" aria-labelledby="fixture-detail-title">
      <div className="m6-panel-title"><h2 id="fixture-detail-title">Chi tiết mẫu · {selected.id}</h2><span className="m6-tag">{selected.status}</span></div>
      <p>{selected.description}</p>
      <dl className="m6-facts"><div><dt>Resource</dt><dd>{selected.resourceType} / {selected.resourceId}</dd></div>
        <div><dt>Reason code mẫu</dt><dd>{selected.reasonCode}</dd></div><div><dt>Severity</dt><dd>{selected.severity}</dd></div>
        <div><dt>Version / timestamps</dt><dd>Chờ API và migration</dd></div></dl>
      {selected.status === "Reported" && <label className="m6-assignee">Người xử lý mẫu<select value={assignee} onChange={e => setAssignee(e.target.value)}><option value="">Chọn người mẫu</option><option>Operator mẫu A</option><option>Operator mẫu B</option></select></label>}
      <div className="m6-toolbar">{allowedFixtureActions(selected.status).map(action => <button className="m6-secondary" type="button" key={action}
        disabled={action === "ASSIGN" && !assignee} onClick={() => transition(action)}>{actionNames[action]}</button>)}
        {selected.status === "Closed" && <p>Closed là terminal theo D07; không có thao tác chuyển trạng thái.</p>}
      </div>
      <p role="status" aria-live="polite">{message}</p>
      <h3>Lịch sử thao tác fixture</h3><ol className="m6-history">{selected.history.map((entry, index) => <li key={`${selected.id}-${index}`}>{entry}</li>)}</ol>
    </section>
  </section>;
}
