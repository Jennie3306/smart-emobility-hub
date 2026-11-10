import { describe, expect, it } from "vitest";
import type { Run, Scenario, Snapshot } from "../api/simulationApi";
import { draftDiffers, draftFromPlan, effectiveScenario, validateDraft } from "./scenarioDraft";

const scenario: Scenario = { name:"Manual closure", hubId:1, disabled:true, injectedRequests:100, durationMins:120, arrivalWindowMins:60, tripMins:30, reroute:false, seed:42 };
const snapshot: Snapshot = { id:"snap", capturedAt:"2026-10-27T00:00:00Z", hubs:[{id:1,name:"A",capacity:40,chargingPoints:2}], vehicles:[], deviceStates:[], demandSource:"MANUAL_SCENARIO", bookingWindow:{from:"",to:"",windowMins:30,hubs:[{hubId:1,bookings:0,activeHolds:0}]} };
const kpi={requests:100,served:0,failed:100,failurePercent:100,meanWaitMins:0,peakQueue:100,hubs:[]};
const run: Run = {id:"run",createdAt:"",scenario,snapshot,kpi,baseline:kpi,timeline:[],recommendations:[],recommendationSource:"LOCAL_RULES",warnings:[]};

describe("scenario draft transitions",()=>{
  it("restores manual demand when trying a saved plan after switching to zero bookings",()=>{
    expect(effectiveScenario(scenario,true,snapshot).injectedRequests).toBe(0);
    const plan=draftFromPlan(run);
    expect(plan.repeatBookings).toBe(false);
    expect(plan.reuse).toBe(run.id);
    const submitted=effectiveScenario(plan.scenario,plan.repeatBookings,snapshot);
    expect(submitted.injectedRequests).toBe(100);
    expect(submitted.reroute).toBe(true);
  });
  it("restores booking demand from the saved window even after manual edits",()=>{
    const booked={...snapshot,demandSource:"M2_BOOKING_VOLUME_REPEAT",bookingWindow:{...snapshot.bookingWindow!,hubs:[{hubId:1,bookings:7,activeHolds:0}]}};
    const plan=draftFromPlan({...run,snapshot:booked});
    expect(effectiveScenario(plan.scenario,plan.repeatBookings,booked).injectedRequests).toBe(7);
    expect(effectiveScenario(plan.scenario,plan.repeatBookings,snapshot).injectedRequests).toBe(0);
  });
  it("marks changed volume, mode or snapshot as unrun without changing saved results",()=>{
    expect(draftDiffers(scenario,false,run.id,run)).toBe(false);
    expect(draftDiffers({...scenario,injectedRequests:500},false,run.id,run)).toBe(true);
    expect(draftDiffers(scenario,true,run.id,run)).toBe(true);
    expect(draftDiffers(scenario,false,"",run)).toBe(true);
    expect(run.kpi.requests).toBe(100);
  });
  it("explains invalid boundaries, empty numbers and arrival beyond horizon",()=>{
    expect(validateDraft({...scenario,durationMins:30},snapshot).arrivalWindowMins).toMatch(/horizon/);
    for(const value of [-1,10001,1.5,NaN]) expect(validateDraft({...scenario,injectedRequests:value}).injectedRequests).toMatch(/whole number/);
    expect(validateDraft(scenario,snapshot)).toEqual({});
    expect(validateDraft({...scenario,injectedRequests:0},snapshot)).toEqual({});
  });
  it("keeps a maximum-length plan name valid",()=>{
    const plan=draftFromPlan({...run,scenario:{...scenario,name:"x".repeat(100)}});
    expect(validateDraft(plan.scenario,snapshot)).toEqual({});
  });
});
