import { afterEach, describe, expect, it } from "vitest";
import type { InternalAxiosRequestConfig } from "axios";
import { api } from "../../../shared/api/client";
import { DEMO_IOT_SEED, simulationApi } from "./simulationApi";

const adapter = api.defaults.adapter;
afterEach(() => { api.defaults.adapter = adapter; });

// Seed 42 reports 15% battery for EB-001 and M3 moves that live vehicle to LOW_BATTERY.
describe("M8 demo IoT tick", () => {
  it("uses the seed that matches the seeded fleet, not the low-battery seed", async () => {
    let sent: InternalAxiosRequestConfig | undefined;
    api.defaults.adapter = async config => { sent = config; return { config, status: 200, statusText: "OK", headers: {}, data: {} }; };
    await simulationApi.iot();
    expect(sent?.url).toBe("/m6/simulator/scenarios/NORMAL_TICK");
    expect(JSON.parse(String(sent?.data))).toEqual({ seed: 100 });
    expect(DEMO_IOT_SEED).not.toBe(42);
  });
});
