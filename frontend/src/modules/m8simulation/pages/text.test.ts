import { describe, expect, it } from "vitest";
import { dispatchSummary, plural, safeNext } from "./text";

describe("plural", () => {
  it("uses the singular only for exactly one", () => {
    expect(`1 ${plural(1, "vehicle")}`).toBe("1 vehicle");
    expect(`0 ${plural(0, "request")}`).toBe("0 requests");
    expect(`2 ${plural(2, "booking")}`).toBe("2 bookings");
  });
});

describe("dispatchSummary", () => {
  it("reports the furthest dispatch state of a run", () => {
    expect(dispatchSummary([])).toBe("No dispatch plan");
    expect(dispatchSummary(["DISCARDED", "APPLIED"])).toBe("Completed in M7");
    expect(dispatchSummary(["PENDING", "NEEDS_REVIEW"])).toBe("Needs review");
    expect(dispatchSummary(["PENDING"])).toBe("Not applied");
  });
});

describe("safeNext", () => {
  it("keeps same-site paths and rejects anything else", () => {
    expect(safeNext("/m8/simulation?tab=history")).toBe("/m8/simulation?tab=history");
    expect(safeNext("//evil.example")).toBe("/");
    expect(safeNext("https://evil.example")).toBe("/");
    expect(safeNext("/login")).toBe("/");
    expect(safeNext(null)).toBe("/");
  });
});
