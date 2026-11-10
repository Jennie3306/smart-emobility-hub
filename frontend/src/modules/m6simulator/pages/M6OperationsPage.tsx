import { useState } from "react";
import { SimulatorPanel } from "../components/SimulatorPanel";
import { IncidentFixturePanel } from "../components/IncidentFixturePanel";
import "../m6.css";

export default function M6OperationsPage() {
  const [section, setSection] = useState<"simulator" | "incidents">("simulator");
  return (
    <div className="m6">
      <header className="m6-heading">
        <div><p className="m6-eyebrow">M6 · Vận hành thiết bị</p><h1>Sự cố & IoT</h1>
          <p>Theo dõi simulator và thử luồng xử lý sự cố của Operator.</p></div>
        <span className="m6-tag">Simulator · API thật</span>
      </header>
      <nav className="m6-switch" aria-label="Chọn khu vực M6">
        <button type="button" aria-pressed={section === "simulator"} onClick={() => setSection("simulator")}>Simulator</button>
        <button type="button" aria-pressed={section === "incidents"} onClick={() => setSection("incidents")}>Incident · Fixture</button>
      </nav>
      <div hidden={section !== "simulator"}><SimulatorPanel /></div>
      <div hidden={section !== "incidents"}><IncidentFixturePanel /></div>
    </div>
  );
}
