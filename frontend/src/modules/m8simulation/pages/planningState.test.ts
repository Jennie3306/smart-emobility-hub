import { expect, it } from "vitest";
import type { Run } from "../api/simulationApi";
import { acceptPlanningResult, refreshActiveRun } from "./planningState";

const a={id:"a",agent:{totalRounds:0}} as Run;
const updated={id:"a",agent:{totalRounds:3}} as Run;
const b={id:"b",agent:{totalRounds:6}} as Run;
it("refresh replaces a stale active session so Retry uses the latest version",()=>{
  expect(refreshActiveRun(a,[updated,b])?.agent?.totalRounds).toBe(3);
  expect(refreshActiveRun(undefined,[updated])).toBeUndefined();
});
it("a late planning response never replaces the run selected since the request started",()=>{
  expect(acceptPlanningResult(b,updated)).toBe(b);
  expect(acceptPlanningResult(a,updated)).toBe(updated);
  expect(acceptPlanningResult(undefined,updated)).toBeUndefined();
});
