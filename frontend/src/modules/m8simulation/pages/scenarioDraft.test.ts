import { describe, expect, it } from "vitest";
import type { Run, Scenario, Snapshot } from "../api/simulationApi";
import { draftDiffers, draftFromPlan, effectiveScenario, hubSettings, validateDraft, validateHubSettings } from "./scenarioDraft";

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

describe("parking and charging hub settings",()=>{
  const capSnapshot: Snapshot = {...snapshot,capacities:[{hubId:1,totalSlots:40,usedSlots:10,freeSlots:30,operationalChargers:6,busyChargers:2}]};
  const hub=()=>({...hubSettings(scenario)[0]});
  const check=(change:object,snap:Snapshot|null=capSnapshot)=>validateHubSettings({...hub(),...change},120,snap??undefined);
  it("defaults to no parking or charging demand",()=>{
    expect(hub()).toMatchObject({parkingArrivals:0,parkingStayMins:60,chargingRequests:0,chargeMins:45,failedChargers:0});
    expect(check({})).toEqual({});
  });
  it("accepts the boundary values and rejects values outside the ranges",()=>{
    expect(check({parkingArrivals:0})).toEqual({});
    expect(check({parkingArrivals:10000})).toEqual({});
    expect(check({parkingArrivals:10001}).parkingArrivals).toMatch(/whole number from 0 to 10000/);
    expect(check({parkingArrivals:1.5}).parkingArrivals).toMatch(/whole number/);
    expect(check({parkingArrivals:NaN}).parkingArrivals).toMatch(/whole number/);
    expect(check({parkingArrivals:5,parkingStayMins:1440})).toEqual({});
    expect(check({parkingArrivals:5,parkingStayMins:1441}).parkingStayMins).toMatch(/0 to 1440/);
    expect(check({chargingRequests:10000})).toEqual({});
    expect(check({chargingRequests:10001}).chargingRequests).toMatch(/0 to 10000/);
    expect(check({chargingRequests:5,chargeMins:480})).toEqual({});
    expect(check({chargingRequests:5,chargeMins:481}).chargeMins).toMatch(/0 to 480/);
    expect(check({failedChargers:-1}).failedChargers).toMatch(/whole number/);
  });
  it("requires a stay or charging time of at least one minute only when there is demand",()=>{
    expect(check({parkingArrivals:0,parkingStayMins:0})).toEqual({});
    expect(check({parkingArrivals:1,parkingStayMins:0}).parkingStayMins).toMatch(/at least 1 minute/);
    expect(check({parkingArrivals:1,parkingStayMins:1})).toEqual({});
    expect(check({chargingRequests:0,chargeMins:0})).toEqual({});
    expect(check({chargingRequests:1,chargeMins:0}).chargeMins).toMatch(/at least 1 minute/);
    expect(check({chargingRequests:1,chargeMins:1})).toEqual({});
  });
  it("limits failed chargers to the operational chargers in the snapshot",()=>{
    expect(check({failedChargers:0})).toEqual({});
    expect(check({failedChargers:6})).toEqual({});
    expect(check({failedChargers:7}).failedChargers).toBe("Failed chargers cannot exceed the 6 operational chargers at this hub.");
    expect(check({failedChargers:1},snapshot).failedChargers).toBe("Failed chargers cannot exceed the 0 operational chargers at this hub.");
    expect(check({failedChargers:1000},null)).toEqual({});
    expect(check({failedChargers:1001},null).failedChargers).toMatch(/0 to 1000/);
  });
  it("does not mark an unchanged old run as changed and sends numbers",()=>{
    const old={...run,scenario:{...scenario,affectedHubs:[{hubId:1,disabled:true,injectedRequests:100,arrivalWindowMins:60,tripMins:30,reroute:false,repeatBookings:false,closeFromMins:0,reopenAtMins:null}]}};
    expect(draftDiffers(old.scenario,false,old.id,old)).toBe(false);
    expect(draftDiffers({...old.scenario,affectedHubs:[{...old.scenario.affectedHubs![0],chargingRequests:5}]},false,old.id,old)).toBe(true);
    const sent=effectiveScenario(old.scenario,false,snapshot).affectedHubs![0];
    expect(sent).toMatchObject({parkingArrivals:0,parkingStayMins:60,chargingRequests:0,chargeMins:45,failedChargers:0});
    expect(validateHubSettings(old.scenario.affectedHubs![0],120,capSnapshot)).toEqual({});
  });
});
