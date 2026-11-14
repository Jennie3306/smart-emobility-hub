import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend } from "recharts";
import { simulationApi as api, type Scenario, type Snapshot, type Run, type Monitor, type Kpi } from "../api/simulationApi";
import "./simulation.css";
import { AgentPanel } from "./AgentPanel";
import { StudioIcon } from "./StudioIcon";
import { refreshActiveRun, acceptPlanningResult } from "./planningState";
import { effectiveScenario, draftFromPlan, numericFields, validateDraft, draftDiffers } from "./scenarioDraft";

const defaults:Scenario={name:"Peak-hour hub closure",hubId:1,disabled:true,injectedRequests:100,durationMins:120,arrivalWindowMins:60,tripMins:30,reroute:false,seed:42};
const number=(n:number)=>n.toLocaleString("en-GB",{maximumFractionDigits:1});
const time=(date:string)=>new Date(date).toLocaleString("en-GB",{hour:"2-digit",minute:"2-digit",day:"2-digit",month:"2-digit"});
function message(error:unknown) { return (error as {response?:{data?:{message?:string}}}).response?.data?.message || "Unable to connect. Please try again."; }

export default function SimulationPage() {
  const [tab,setTab]=useState<"studio"|"history"|"network">("studio");
  const [snapshot,setSnapshot]=useState<Snapshot>(); const [monitor,setMonitor]=useState<Monitor>();
  const [runs,setRuns]=useState<Run[]>([]); const [active,setActive]=useState<Run>();
  const [scenario,setScenario]=useState(defaults); const [text,setText]=useState("Flooding at Metro Hub, about 100 students need rides over the next 120 minutes.");
  const [busy,setBusy]=useState(false); const [error,setError]=useState(""); const [notice,setNotice]=useState("");
  const [compare,setCompare]=useState<string[]>([]); const [reuse,setReuse]=useState<string>("");
  const [repeatBookings,setRepeatBookings]=useState(false);
  const inputSnapshot=runs.find(r=>r.id===reuse)?.snapshot || snapshot;
  const observed=inputSnapshot?.bookingWindow?.hubs.find(h=>h.hubId===scenario.hubId)?.bookings ?? 0;
  const effective=effectiveScenario(scenario,repeatBookings,inputSnapshot);
  const fieldErrors=validateDraft(effective,inputSnapshot);
  if(repeatBookings&&!inputSnapshot?.bookingWindow) fieldErrors.requestSource="This snapshot has no booking window. Choose manual volume or another snapshot.";
  const invalid=Object.keys(fieldErrors).length>0;
  const dirty=draftDiffers(effective,repeatBookings,reuse,active);
  function edit() {setError("");setNotice("");}
  function navigate(next:typeof tab) {setTab(next);edit();}
  async function refresh() {
    const [s,m,r]=await Promise.all([api.snapshot(),api.status(),api.list()]);setSnapshot(s);setMonitor(m);setRuns(r);setActive(current=>refreshActiveRun(current,r));
  }
  useEffect(()=>{refresh().catch(e=>setError(message(e)));},[]);
  async function task(action:()=>Promise<void>) {setBusy(true);setError("");setNotice("");try {await action();} catch(e){if((e as {response?:{data?:{code?:string}}}).response?.data?.code==="STALE_AGENT_STATE")await refresh().catch(()=>{});setError(message(e));} finally{setBusy(false);} }
  function update(key:keyof Scenario,value:Scenario[keyof Scenario]) {edit();setScenario(s=>({...s,[key]:value}));}
  function load(run:Run) {setError("");setActive(run);setScenario(run.scenario);setReuse(run.id);setRepeatBookings(run.snapshot.demandSource==="M2_BOOKING_VOLUME_REPEAT");setTab("studio");setNotice("Reusing the saved snapshot for a fair comparison. You can also capture a new snapshot.");}
  const selected=runs.filter(r=>compare.includes(r.id));
  const hubs=active?.snapshot.hubs || snapshot?.hubs || [];
  const hubName=(id:number)=>hubs.find(h=>h.id===id)?.name || `Hub ${id}`;
  const renderNumericField=([key,label,min,max]:typeof numericFields[number])=><label key={key} htmlFor={`scenario-${key}`}>{label}<input id={`scenario-${key}`} name={key} autoComplete="off" aria-label={label} type="number" min={min} max={max} step={1} aria-invalid={!!fieldErrors[key]} aria-describedby={`hint-${key}${fieldErrors[key]?` error-${key}`:""}`} disabled={key==="injectedRequests"&&repeatBookings} value={Number.isFinite(effective[key])?effective[key]:""} onChange={e=>update(key,e.target.valueAsNumber)}/><span id={`hint-${key}`} className="m8-field-hint">{{injectedRequests:"One request is one attempted rental.",durationMins:"How long the simulation runs.",arrivalWindowMins:"Requests arrive within this window.",tripMins:"Time before a rented vehicle returns.",seed:"Same seed repeats the arrival pattern."}[key]}</span>{fieldErrors[key]&&<span id={`error-${key}`} className="m8-field-error">{fieldErrors[key]}</span>}</label>;
  return <div className="m8-app">
    <a className="m8-skip" href="#m8-workspace">Skip to workspace</a>
    <aside className="m8-sidebar">
      <div className="m8-logo"><span><StudioIcon kind="arrow"/></span><div>MOBILITY LAB<small>Operations · What-if Studio</small></div></div>
      <div className="m8-nav-label">WORKSPACE</div>
      <button className={tab==="studio"?"active":""} aria-current={tab==="studio"?"page":undefined} onClick={()=>navigate("studio")}><StudioIcon kind="scenario"/> What-if simulation</button>
      <button className={tab==="history"?"active":""} aria-current={tab==="history"?"page":undefined} onClick={()=>navigate("history")}><StudioIcon kind="history"/> History & comparison <b>{runs.length}</b></button>
      <button className={tab==="network"?"active":""} aria-current={tab==="network"?"page":undefined} onClick={()=>navigate("network")}><StudioIcon kind="network"/> Network snapshot</button>
      <div className="m8-side-note"><span className="m8-dot"/> Simulation only<p>Explore changes on a copy of your network. Live vehicles stay unchanged.</p></div>
      <div className="m8-operator"><span>OP</span><div>Operator workspace<small>M8 · Simulation & Recommendation</small></div></div>
    </aside>
    <div className="m8-main">
      <header className="m8-top"><span>Smart E-Mobility Hub <i>/</i> M8</span><span className="m8-chip">Local preview</span></header>
      <nav className="m8-mobile-nav" aria-label="M8 workspace">
        <button aria-current={tab==="studio"?"page":undefined} onClick={()=>navigate("studio")}>What-if simulation</button>
        <button aria-current={tab==="history"?"page":undefined} onClick={()=>navigate("history")}>History & comparison ({runs.length})</button>
        <button aria-current={tab==="network"?"page":undefined} onClick={()=>navigate("network")}>Network snapshot</button>
      </nav>
      <div className="m8-content" id="m8-workspace" tabIndex={-1}>
        <div className="m8-title"><div><h1>{tab==="studio"?"What-if Simulation":tab==="history"?"History & Comparison":"Network Snapshot"}</h1><p>{tab==="studio"?"Test a network change, see its impact, and let AI explore better plans.":tab==="history"?"Select two runs to compare KPIs. Reuse the same snapshot and seed to isolate scenario impact.":"Hub and vehicle data from PostgreSQL, booking counts from M2 and resource observations from M1/M6."}</p></div><button className="m8-button secondary" disabled={busy} onClick={()=>task(refresh)}><StudioIcon kind="refresh"/>Refresh</button></div>
        {tab==="studio"&&<ol className="m8-workflow" aria-label="Simulation workflow"><li><span>1</span><div><strong>Set the scenario</strong><small>Describe it or edit the form</small></div></li><li><span>2</span><div><strong>Run the simulation</strong><small>Code calculates the KPIs</small></div></li><li><span>3</span><div><strong>Explore AI plans</strong><small>Test, read feedback, refine</small></div></li></ol>}
        {busy&&<div className="m8-alert" role="status">Processing… AI planning can take a few minutes.</div>}
        {error && <div className="m8-alert error" role="alert">{error}</div>}
        {notice && <div className="m8-alert" role="status">{notice}</div>}
        {!snapshot && !error && <div className="m8-panel">Loading network…</div>}
        {snapshot && tab==="studio" && <>
          <div className="m8-work-grid">
            <section className="m8-panel m8-form">
              <div className="m8-section-head"><span className="m8-step" aria-hidden="true">1</span><div><h2>Scenario Inputs</h2><p>Choose what changes and how much demand to test.</p></div></div>
              <div className="m8-assistant"><div><strong>Describe the situation</strong><span className="m8-chip">{monitor?.aiConfigured?"AI ready":"Local rules"}</span></div><p>AI fills a draft. Review the values before running.</p><textarea name="description" autoComplete="off" aria-label="Scenario description" value={text} onChange={e=>setText(e.target.value)}/><button className="m8-button secondary" disabled={busy||!text.trim()} onClick={()=>task(async()=>{const result=await api.parse(text);setScenario(result.scenario);setRepeatBookings(false);setNotice(`${result.source==="LLM"?"AI":"Local rules"} filled the form. Review before running. ${result.warnings.join(" ")}`);})}>Fill Form from Description</button></div>
              <label>Scenario name<input name="scenarioName" autoComplete="off" aria-label="Scenario name" aria-invalid={!!fieldErrors.name} aria-describedby={fieldErrors.name?"error-name":undefined} maxLength={100} value={scenario.name} onChange={e=>update("name",e.target.value)}/>{fieldErrors.name&&<span id="error-name" className="m8-field-error">{fieldErrors.name}</span>}</label>
              <label>Affected hub<select name="hubId" aria-describedby={fieldErrors.hubId?"scenario-errors":undefined} aria-invalid={!!fieldErrors.hubId} value={scenario.hubId} onChange={e=>update("hubId",Number(e.target.value))}>{snapshot.hubs.map(h=><option key={h.id} value={h.id}>{h.name}</option>)}</select></label>
              <fieldset className="m8-demand"><legend>Rental Request Volume</legend><div className="m8-demand-options">
                <label className={!repeatBookings?"selected":""}><input type="radio" name="requestSource" value="manual" checked={!repeatBookings} aria-describedby="request-source-help" onChange={()=>{edit();setScenario(effective);setRepeatBookings(false);}}/><span><strong>Enter a volume</strong><small>Test a what-if number</small></span></label>
                <label className={repeatBookings?"selected":""}><input type="radio" name="requestSource" value="bookings" checked={repeatBookings} disabled={!inputSnapshot?.bookingWindow} aria-describedby="request-source-help" onChange={()=>{edit();setScenario(effective);setRepeatBookings(true);}}/><span><strong>Use recent bookings</strong><small>Captured 30-minute count</small></span></label>
              </div><p id="request-source-help" className="m8-field-hint">{repeatBookings?`${observed} accepted bookings at ${inputSnapshot?.hubs.find(h=>h.id===scenario.hubId)?.name??"this hub"}. The count comes from the selected snapshot, not all attempted rentals.`:"Enter how many rental requests should arrive at the affected hub. This is a hypothetical volume, not measured demand."}</p>
              {repeatBookings&&observed===0&&<p className="m8-demand-warning" role="status">No accepted bookings in this window. This will simulate 0 requests. Choose Enter a volume to test demand.</p>}
              {!inputSnapshot?.bookingWindow&&<p className="m8-field-hint">This older snapshot has no booking count. Enter a volume or choose a newer snapshot.</p>}
              {repeatBookings&&inputSnapshot?.bookingWindow&&<p className="m8-field-hint">Captured window: {time(inputSnapshot.bookingWindow.from)}–{time(inputSnapshot.bookingWindow.to)}. Searches and rejected attempts are excluded.</p>}
              </fieldset>
              <div className="m8-field-grid">{numericFields.filter(([key])=>key!=="seed").map(renderNumericField)}</div>
              <label className="m8-toggle"><input type="checkbox" checked={scenario.disabled} onChange={e=>update("disabled",e.target.checked)}/><span><strong>Simulate this hub being closed</strong><small>No rentals can start at this hub during the simulation.</small></span></label>
              <label className="m8-toggle"><input type="checkbox" checked={scenario.reroute} onChange={e=>update("reroute",e.target.checked)}/><span><strong>Let riders rent from another hub</strong><small>If this hub cannot serve a request, try another open hub with a vehicle available. Extra travel time is not counted.</small></span></label>
              <details className="m8-advanced"><summary>Snapshot & Repeatability</summary><p>Use the same snapshot and seed to compare scenarios fairly.</p><label>Snapshot source<select name="snapshotSource" value={reuse} onChange={e=>{edit();setReuse(e.target.value);}}><option value="">Capture the live network</option>{runs.map(r=><option key={r.id} value={r.id}>{r.scenario.name} · {time(r.createdAt)}</option>)}</select></label>{numericFields.filter(([key])=>key==="seed").map(renderNumericField)}</details>
              <p className="m8-input-summary">{Number.isFinite(effective.injectedRequests)?number(effective.injectedRequests):"—"} rental requests · {Number.isFinite(scenario.durationMins)?scenario.durationMins:"—"} min · {scenario.disabled?"hub closed":"hub open"}</p>
              {invalid&&<div id="scenario-errors" className="m8-alert error" role="alert">Correct the highlighted fields before running.<ul>{Object.values(fieldErrors).map(e=><li key={e}>{e}</li>)}</ul></div>}
              <button className="m8-button primary m8-run" disabled={busy||invalid} aria-describedby={invalid?"scenario-errors":undefined} onClick={()=>task(async()=>{if(invalid)return;const run=await api.run(effective,reuse||undefined,repeatBookings);setScenario(run.scenario);setReuse(run.id);setRepeatBookings(run.snapshot.demandSource==="M2_BOOKING_VOLUME_REPEAT");setActive(run);setRuns(await api.list());setNotice("Results and planning history saved. Live vehicle data was not changed.");})}>{busy?"Processing…":"Save & Run Simulation"}</button>
            </section>
            <section className="m8-results">
              <div className="m8-panel m8-preview"><div className="m8-section-head"><span className="m8-step" aria-hidden="true">2</span><div><h2>Scenario Results</h2><p>{active?`${active.scenario.name} · ${time(active.createdAt)}`:"Calculated by the simulator after you confirm the inputs."}</p></div><span className="m8-chip">{active?(dirty?"Draft changed":"Saved"):"Ready"}</span></div>
                <div className="m8-empty">
                  <h3>Starting network</h3>
                  <p>{active ? "Vehicles available in the snapshot used for this run." : "Vehicles available before the simulation."}</p>
                  <div className="m8-mini-network">{(active?.snapshot ?? inputSnapshot ?? snapshot).hubs.map(hub => {
                    const available = (active?.snapshot ?? inputSnapshot ?? snapshot).vehicles.filter(
                      vehicle => vehicle.hubId === hub.id && vehicle.state === "AVAILABLE" && vehicle.battery >= 20,
                    ).length;
                    return <span key={hub.id}>{hub.name}<b>{available} available {available === 1 ? "vehicle" : "vehicles"}</b></span>;
                  })}</div>
                </div>
                {active?<>{dirty&&<div className="m8-alert" role="status">Results and recommendations below are from the previous run. Your draft changes have not been simulated.</div>}<div className="m8-kpis"><Metric label="Requests served" value={number(active.kpi.served)} unit={`/ ${active.kpi.requests}`} /><Metric label="Unserved at end" value={number(active.kpi.failurePercent)} unit="%" danger={active.kpi.failurePercent>0}/><Metric label="Avg. wait · served only" value={active.kpi.served?number(active.kpi.meanWaitMins):"—"} unit={active.kpi.served?"min":""}/><Metric label="Peak queue" value={number(active.kpi.peakQueue)} unit="requests"/></div>
                  <div className="m8-baseline">Same network with the hub open: <strong>{number(active.baseline.failurePercent)}% failed</strong><span>Difference {number(active.kpi.failurePercent-active.baseline.failurePercent)} percentage points</span></div>
                  <div className="m8-chart"><ResponsiveContainer width="100%" height={240}><LineChart accessibilityLayer data={active.timeline}><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis dataKey="minute" tick={{fontSize:11}} minTickGap={30} unit="′"/><YAxis tick={{fontSize:11}}/><Tooltip/><Legend/><Line name="Served" type="monotone" dataKey="served" stroke="#1F4E79" strokeWidth={2.5} dot={false}/><Line name="Waiting" type="monotone" dataKey="waiting" stroke="#a15c07" strokeDasharray="5 3" strokeWidth={2.5} dot={false}/></LineChart></ResponsiveContainer></div>
                  <p className="m8-footnote">Mean wait includes served requests only. Requests still waiting at the horizon count as failed.</p>
                  <div className="m8-table-wrap" tabIndex={0} role="region" aria-label="Results by serving hub"><table><thead><tr><th>Serving hub</th><th>Served</th><th>Failed</th><th>Vehicles available at horizon</th></tr></thead><tbody>{active.kpi.hubs.map(h=><tr key={h.hubId}><td>{h.name}</td><td>{h.served}</td><td>{h.failed}</td><td>{h.availableVehicles}</td></tr>)}</tbody></table></div>
                </>:null}

              </div>
              {active&&<AgentPanel run={active} busy={busy} configured={!!monitor?.aiConfigured} onRetry={()=>task(async()=>{const updated=await api.retry(active.id,active.agent?.totalRounds??0);setActive(current=>acceptPlanningResult(current,updated));setRuns(await api.list());setNotice(`AI planning saved for ${updated.scenario.name}. The original snapshot and scenario are unchanged.`);})}/>}
              {(!active?.agent || active.recommendations.length > 0) && <div className="m8-panel"><div className="m8-section-head"><span className="m8-step" aria-hidden="true">3</span><div><h2>Recommended plans</h2><p>Projected impact is checked in simulation. M7 checks current destination parking through M4.</p></div>{active && <span className="m8-chip">{active.recommendationSource === "TOOL_AGENT" ? "AI tool exploration" : active.recommendationSource === "LLM" ? "AI-assisted projection" : active.recommendationSource === "LLM_AND_LOCAL_RULES" ? "AI + local rules" : "Local rules"}</span>}</div>
                {active?.recommendations.length ? active.recommendations.map(r => (
                  <div className="m8-recommendation" key={r.id}>
                    <div className="m8-rec-icon"><StudioIcon kind="network"/></div>
                    <div>
                      <h3>{r.action === "reroute" ? "Route requests to available hubs" : `Dispatch ${r.amount} vehicles`}</h3>
                      {r.action === "dispatch" && <p>{hubName(r.fromHubId)} → {hubName(r.toHubId)}</p>}
                      <p className="m8-footnote">{r.reason}</p>
                      <strong className="m8-benefit">{r.id.startsWith("agent-") ? "Full plan failure rate" : "Projected failure rate"}: {number(r.projectedFailurePercent)}%</strong>
                      {r.dispatch && <>
                        <p role="status">{r.dispatch.tasks.length}/{r.amount} tasks created{r.status === "NEEDS_REVIEW" ? " · Needs review" : ""}</p>
                        {r.dispatch.tasks.length > 0 && <ul>{r.dispatch.tasks.map(task => <li key={task.taskId}>Task #{task.taskId}{task.vehicleId != null && ` · Vehicle #${task.vehicleId}`}</li>)}</ul>}
                        {r.dispatch.m7RecommendationId && <Link to="/m7/redistribution">View M7 recommendation #{r.dispatch.m7RecommendationId}</Link>}
                        {r.dispatch.message && <p className="m8-footnote">{r.dispatch.message}</p>}
                        {r.dispatch.unconfirmedVehicleId != null && <p className="m8-footnote">Review vehicle #{r.dispatch.unconfirmedVehicleId}.</p>}
                      </>}
                      <div className="m8-actions">
                        {r.action === "reroute" ? (
                          <button className="m8-button primary" disabled={busy || r.status !== "PENDING"} onClick={() => {
                            const plan = draftFromPlan(active);
                            edit(); setScenario(plan.scenario); setReuse(plan.reuse); setRepeatBookings(plan.repeatBookings);
                            setNotice("Rerouting parameters filled. Save and run to verify them on the same snapshot.");
                          }}>Try this plan</button>
                        ) : (
                          <button className="m8-button primary" disabled={busy || !monitor?.applyEnabled || !["PENDING", "PARTIAL"].includes(r.status)}
                            onClick={() => task(async () => {
                              const updated = await api.apply(active.id, r.id);
                              setActive(updated);
                              setRuns(current => current.map(run => run.id === updated.id ? updated : run));
                            })}>{r.status === "PARTIAL" ? "Create remaining tasks" : "Approve & execute"}</button>
                        )}
                        <button className="m8-button secondary" disabled={busy || r.status !== "PENDING"}
                          onClick={() => task(async () => { setActive(await api.discard(active.id, r.id)); setRuns(await api.list()); })}>
                          {r.status === "DISCARDED" ? "Discarded" : "Discard"}
                        </button>
                      </div>
                    </div>
                  </div>
                )) : <p className="m8-muted">{active ? "No actionable recommendation for this run." : "Run a scenario to find a plan that fits available resources."}</p>}
                <div className="m8-integration-note">{monitor?.applyEnabled
                  ? "Apply creates M7 tasks. M7 checks destination parking when it creates and completes each task."
                  : "Dispatch availability is loading."}</div>
              </div>}
              {active && <details className="m8-panel">
                <summary>Model assumptions & limitations</summary>
                <ul>
                  <li>Only the affected hub generates requests. Other hubs have no demand of their own in this run, so rerouting or moving vehicles may look more effective than it would in practice.</li>
                  {active.warnings.map((w,i)=><li key={i}>{w}</li>)}
                </ul>
                <p>Request source: {active.snapshot.demandSource==="M2_BOOKING_VOLUME_REPEAT"?"Repeated accepted bookings":"Manual scenario"}. Recommendation source: {active.recommendationSource==="TOOL_AGENT"?"AI tool exploration":active.recommendationSource==="LLM"?"OpenAI":active.recommendationSource==="LLM_AND_LOCAL_RULES"?"OpenAI + local rules":"Local rules"}. AVAILABLE vehicles with battery ≥20%; trips return to their origin. Charging and battery depletion are not modeled.</p>
              </details>}
            </section>
          </div>
        </>}
        {tab==="history" && <><section className="m8-panel"><div className="m8-section-head"><h2>Saved runs</h2><span className="m8-chip">{compare.length}/2 selected for comparison</span></div>{runs.length===0?<p className="m8-muted">No saved runs yet. Create your first scenario in What-if Studio.</p>:<div className="m8-table-wrap" tabIndex={0} role="region" aria-label="Saved runs table"><table><thead><tr><th>Select</th><th>Scenario</th><th>Created</th><th>Served</th><th>Failed</th><th>Wait · served only</th><th/></tr></thead><tbody>{runs.map(r=><tr key={r.id}><td><input aria-label={`Compare ${r.scenario.name}`} type="checkbox" checked={compare.includes(r.id)} disabled={!compare.includes(r.id)&&compare.length===2} onChange={e=>setCompare(e.target.checked?[...compare,r.id]:compare.filter(id=>id!==r.id))}/></td><td><strong>{r.scenario.name}</strong><small>{r.scenario.injectedRequests} request · seed {r.scenario.seed}</small></td><td>{time(r.createdAt)}</td><td>{r.kpi.served}</td><td>{number(r.kpi.failurePercent)}%</td><td>{r.kpi.served?`${number(r.kpi.meanWaitMins)} min`:"—"}</td><td><button className="m8-button secondary" onClick={()=>load(r)}>View & edit</button></td></tr>)}</tbody></table></div>}</section>{selected.length===2&&<section className="m8-panel m8-comparison"><h2>KPI comparison</h2>{selected[0].snapshot.id!==selected[1].snapshot.id&&<div className="m8-alert">These runs use different snapshots. Differences may reflect changes in the network.</div>}{selected[0].scenario.seed!==selected[1].scenario.seed&&<div className="m8-alert">Seeds differ, so request arrival times are not identical.</div>}<div className="m8-table-wrap" tabIndex={0} role="region" aria-label="KPI comparison table"><table><thead><tr><th>Metric</th>{selected.map(r=><th key={r.id}>{r.scenario.name}</th>)}</tr></thead><tbody>{([['requests','Requests'],['served','Served'],['failed','Failed'],['failurePercent','Failure rate (%)'],['meanWaitMins','Mean wait · served only (min)'],['peakQueue','Peak queue']] as [keyof Kpi,string][]).map(([key,label])=><tr key={key}><td>{label}</td>{selected.map(r=><td key={r.id}>{key==="meanWaitMins"&&r.kpi.served===0?"—":number(r.kpi[key] as number)}</td>)}</tr>)}</tbody></table></div></section>}</>}
        {tab==="network"&&snapshot&&<><div className="m8-network-head"><span className="m8-chip">Snapshot · {time(snapshot.capturedAt)}</span><button className="m8-button secondary" disabled={busy} onClick={()=>task(async()=>{await api.iot();await refresh();setNotice("Received M6 demo observations. They are displayed separately from PostgreSQL vehicle state.");})}><StudioIcon kind="refresh"/>Read Demo IoT Observations</button></div><div className="m8-hub-grid">{snapshot.hubs.map(h=>{const vehicles=snapshot.vehicles.filter(v=>v.hubId===h.id);return <section className="m8-panel" key={h.id}><span className="m8-chip">HUB {String(h.id).padStart(2,'0')}</span><h2>{h.name}</h2><div className="m8-hub-count">{vehicles.filter(v=>v.state==="AVAILABLE"&&v.battery>=20).length}<small> available vehicles</small></div><p className="m8-footnote">{vehicles.length} assigned vehicles · {h.capacity} parking spaces · {h.chargingPoints} chargers</p>{vehicles.map(v=><div className="m8-vehicle" key={v.id}><span>Vehicle #{v.id}</span><span>{v.battery}% · {v.state}</span></div>)}</section>;})}</div><section className="m8-panel"><h2>M6 device observations</h2>{snapshot.deviceStates.length?<div className="m8-table-wrap" tabIndex={0} role="region" aria-label="Device observations"><table><thead><tr><th>Resource</th><th>Hub</th><th>State</th><th>Received</th></tr></thead><tbody>{snapshot.deviceStates.map(d=><tr key={`${d.type}:${d.resourceId}`}><td>{d.type} #{d.resourceId}</td><td>{hubName(d.hubId)}</td><td>{d.state}</td><td>{time(d.observedAt)}</td></tr>)}</tbody></table></div>:<p className="m8-muted">No M6 observations received in this backend session.</p>}</section></>}
        {tab==="network"&&monitor&&<section className="m8-panel"><div className="m8-section-head"><div><h2>Repeat recent booking volume</h2><p>{monitor.demandDescription}</p></div><button className="m8-button secondary" disabled={busy} onClick={()=>task(async()=>{setMonitor(await api.check());setSnapshot(await api.snapshot());setNotice("Booking-volume check completed. Live vehicles were not changed.");})}>Run check now</button></div>{monitor.warnings.map((warning,i)=><p className="m8-muted" key={i}>{warning}</p>)}<div className="m8-table-wrap" tabIndex={0} role="region" aria-label="Booking volume forecast"><table><thead><tr><th>Hub</th><th>Bookings / 30 min</th><th>Active holds</th><th>Projected served</th><th>Projected failed</th></tr></thead><tbody>{monitor.forecasts?.map(f=><tr key={f.hubId}><td>{f.hubName}</td><td>{f.recentBookings}</td><td>{f.activeHolds}</td><td>{f.projectedServed}</td><td>{f.projectedFailed}</td></tr>)}</tbody></table></div></section>}
        {tab==="network"&&snapshot?.capacities&&<section className="m8-panel"><h2>M1 resource observations</h2><p className="m8-footnote">Monitoring read model, including seeded demo observations. This is not a parking reservation service.</p><div className="m8-table-wrap" tabIndex={0} role="region" aria-label="Observed hub capacity"><table><thead><tr><th>Hub</th><th>Used / total parking</th><th>Free parking</th><th>Busy / operational chargers</th></tr></thead><tbody>{snapshot.capacities.map(c=><tr key={c.hubId}><td>{snapshot.hubs.find(h=>h.id===c.hubId)?.name}</td><td>{c.usedSlots} / {c.totalSlots}</td><td>{c.freeSlots}</td><td>{c.busyChargers} / {c.operationalChargers}</td></tr>)}</tbody></table></div></section>}
        <footer className="m8-footer"><span>Network checks every 30 minutes {monitor?.lastChecked&&`· Last checked ${time(monitor.lastChecked)}`}</span><span>Accepted bookings connected · No automatic execution</span></footer>
      </div>
    </div>
  </div>;
}
function Metric({label,value,unit,danger=false}:{label:string;value:string;unit:string;danger?:boolean}) {return <div className={`m8-metric ${danger?'danger':''}`}><span>{label}</span><strong>{value}<small>{unit}</small></strong></div>;}
