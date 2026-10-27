import type { Run, Scenario, Snapshot } from "../api/simulationApi";

export function effectiveScenario(draft: Scenario, repeatBookings: boolean, snapshot?: Snapshot): Scenario {
  if (!repeatBookings) return draft;
  const count = snapshot?.bookingWindow?.hubs.find(h => h.hubId === draft.hubId)?.bookings ?? 0;
  return { ...draft, injectedRequests: count };
}

export function draftFromPlan(run: Run) {
  return {
    scenario: { ...run.scenario, name: `${run.scenario.name.slice(0, 88)} · rerouting`, reroute: true },
    reuse: run.id,
    repeatBookings: run.snapshot.demandSource === "M2_BOOKING_VOLUME_REPEAT",
  };
}

export const numericFields = [
  ["injectedRequests", "Rental requests", 0, 10000],
  ["durationMins", "Simulation horizon (min)", 1, 1440],
  ["arrivalWindowMins", "Arrival window (min)", 1, 1440],
  ["tripMins", "Trip duration (min)", 1, 240],
  ["seed", "Random seed", 0, 2147483647],
] as const;

export function validateDraft(s: Scenario, snapshot?: Snapshot) {
  const errors: Partial<Record<keyof Scenario | "requestSource", string>> = {};
  if (!s.name.trim() || s.name.length > 100) errors.name = "Enter a scenario name of 1–100 characters.";
  for (const [key, label, min, max] of numericFields) {
    if (!Number.isInteger(s[key]) || s[key] < min || s[key] > max)
      errors[key] = `${label} must be a whole number from ${min} to ${max}.`;
  }
  if (!errors.arrivalWindowMins && !errors.durationMins && s.arrivalWindowMins > s.durationMins)
    errors.arrivalWindowMins = "Arrival window must not exceed the simulation horizon.";
  if (snapshot && !snapshot.hubs.some(h => h.id === s.hubId)) errors.hubId = "Select an available hub.";
  return errors;
}

export function draftDiffers(scenario: Scenario, repeatBookings: boolean, reuse: string, active?: Run) {
  return !!active && ((Object.keys(scenario) as (keyof Scenario)[]).some(key => scenario[key] !== active.scenario[key])
    || repeatBookings !== (active.snapshot.demandSource === "M2_BOOKING_VOLUME_REPEAT") || reuse !== active.id);
}
