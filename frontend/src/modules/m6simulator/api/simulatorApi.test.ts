import { afterEach, describe, expect, it } from "vitest";
import type { InternalAxiosRequestConfig } from "axios";
import { api } from "../../../shared/api/client";
import { normalizeSeed, simulatorApi } from "./simulatorApi";

const adapter = api.defaults.adapter;
afterEach(() => { api.defaults.adapter = adapter; });

describe("seed transport", () => {
  it.each(["-9223372036854775808", "9223372036854775807", "9007199254740993", "0"])("preserves numeric Long %s in both directions", async seed => {
    let sent: InternalAxiosRequestConfig | undefined;
    api.defaults.adapter = async config => {
      sent = config;
      return { config, status: 200, statusText: "OK", headers: {}, data: `{"seed":${seed},"outboundEvents":[]}` };
    };
    const result = await simulatorApi.run("NORMAL_TICK", seed);
    expect(sent?.url).toBe("/m6/simulator/scenarios/NORMAL_TICK");
    expect(sent?.data).toBe(`{"seed":${seed}}`);
    expect(sent?.withCredentials).toBe(true);
    expect(result.seed).toBe(seed);
  });

  it.each(["", " ", "1.2", "1e3", "abc", "9223372036854775808", "-9223372036854775809"])("rejects invalid seed %j before sending", input => {
    expect(() => normalizeSeed(input)).toThrow();
  });

  it("normalizes leading zeroes and preserves a nested latestRun seed", async () => {
    expect(normalizeSeed(" -00042 ")).toBe("-42");
    api.defaults.adapter = async config => ({ config, status: 200, statusText: "OK", headers: {},
      data: '{"schedulerEnabled":false,"intervalMs":null,"state":"SUCCEEDED","latestRun":{"seed":9223372036854775807}}' });
    expect((await simulatorApi.status()).latestRun?.seed).toBe("9223372036854775807");
  });
});
