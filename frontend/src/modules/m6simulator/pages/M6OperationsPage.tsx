import { useState } from "react";
import { SimulatorPanel } from "../components/SimulatorPanel";
import { IncidentPanel } from "../components/IncidentPanel";
import { PageHeader, Tabs } from "@/shared/ui";
import "../m6.css";

export default function M6OperationsPage() {
  const [section, setSection] = useState<"incidents" | "simulator">("incidents");
  return (
    <div className="m6">
      <PageHeader module="M6" eyebrow="IoT simulator & incidents" title="Incidents & device events"
        description="Report and work through device incidents, and run the IoT simulator. Owners decide what a reading means; M6 only reports it."
        actions={<span className="m6-tag">Live backend API</span>} />
      <Tabs label="M6 sections" value={section} onChange={setSection}
        tabs={[{ id: "incidents", label: "Incident workflow" }, { id: "simulator", label: "Simulator events" }]} />
      <div hidden={section !== "incidents"}><IncidentPanel /></div>
      <div hidden={section !== "simulator"}><SimulatorPanel /></div>
    </div>
  );
}
