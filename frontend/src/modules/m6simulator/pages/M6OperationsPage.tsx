import { useState } from "react";
import { SimulatorPanel } from "../components/SimulatorPanel";
import { IncidentPanel } from "../components/IncidentPanel";
import "../m6.css";

export default function M6OperationsPage() {
  const [section, setSection] = useState<"simulator" | "incidents">("simulator");
  return (
    <div className="m6">
      <header className="m6-heading">
        <div><p className="m6-eyebrow">M6 · Vận hành thiết bị</p><h1>Sự cố & IoT</h1>
          <p>Theo dõi simulator, báo cáo và xử lý sự cố thiết bị.</p></div>
        <span className="m6-tag">Simulator · Sự cố · API thật</span>
      </header>
      <nav className="m6-switch" aria-label="Chọn khu vực M6">
        <button type="button" aria-pressed={section === "simulator"} onClick={() => setSection("simulator")}>Simulator</button>
        <button type="button" aria-pressed={section === "incidents"} onClick={() => setSection("incidents")}>Sự cố</button>
      </nav>
      <div hidden={section !== "simulator"}><SimulatorPanel /></div>
      <div hidden={section !== "incidents"}><IncidentPanel /></div>
    </div>
  );
}
