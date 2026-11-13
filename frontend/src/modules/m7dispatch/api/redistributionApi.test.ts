import { describe, expect, it } from "vitest";
import { nextStatuses } from "./redistributionApi";

describe("task lifecycle buttons", () => {
  it("offers only the transitions of the task state machine", () => {
    expect(nextStatuses.CREATED).toEqual(["IN_PROGRESS", "FAILED"]);
    expect(nextStatuses.IN_PROGRESS).toEqual(["DONE", "FAILED"]);
    expect(nextStatuses.DONE).toEqual([]);
    expect(nextStatuses.FAILED).toEqual([]);
  });
});
