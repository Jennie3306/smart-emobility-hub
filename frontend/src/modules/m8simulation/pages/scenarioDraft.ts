import type { HubSettings, Run, Scenario, Snapshot } from "../api/simulationApi";

export function hubSettings(scenario: Scenario, repeatBookings = false): HubSettings[] {
  if (scenario.affectedHubs) return scenario.affectedHubs;
  const {hubId, disabled, injectedRequests, arrivalWindowMins, tripMins, reroute} = scenario;
  return [{hubId, disabled, injectedRequests, arrivalWindowMins, tripMins, reroute,
    repeatBookings, closeFromMins: 0, reopenAtMins: null}];
}

export function withHubSettings(scenario: Scenario, settings: HubSettings[]): Scenario {
  const first = settings[0];
  return {...scenario, hubId: first.hubId, disabled: first.disabled, injectedRequests: first.injectedRequests,
    arrivalWindowMins: first.arrivalWindowMins, tripMins: first.tripMins, reroute: first.reroute, affectedHubs: settings};
}

export function effectiveScenario(draft: Scenario, repeatBookings: boolean, snapshot?: Snapshot): Scenario {
  if (draft.affectedHubs) return withHubSettings(draft, draft.affectedHubs.map(hub => hub.repeatBookings
    ? {...hub, injectedRequests: snapshot?.bookingWindow?.hubs.find(h => h.hubId === hub.hubId)?.bookings ?? 0}
    : hub));
  if (!repeatBookings) return draft;
  const count = snapshot?.bookingWindow?.hubs.find(h => h.hubId === draft.hubId)?.bookings ?? 0;
  return { ...draft, injectedRequests: count };
}

export function draftFromPlan(run: Run, hubId = run.scenario.hubId) {
  const scenario = {...run.scenario, name: `${run.scenario.name.slice(0, 88)} · rerouting`};
  const repeatBookings = run.snapshot.demandSource === "M2_BOOKING_VOLUME_REPEAT";
  return {
    scenario: run.scenario.affectedHubs
      ? withHubSettings(scenario, hubSettings(scenario).map(h => h.hubId === hubId ? {...h, reroute: true} : h))
      : {...scenario, reroute: true},
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

export const hubNumberFields = [numericFields[0], numericFields[2], numericFields[3]] as const;

export function validateHubSettings(hub: HubSettings, horizon: number, snapshot?: Snapshot) {
  const errors: Partial<Record<keyof HubSettings, string>> = {};
  for (const [key, label, min, max] of hubNumberFields) {
    if (!Number.isInteger(hub[key]) || hub[key] < min || hub[key] > max)
      errors[key] = `${label} must be a whole number from ${min} to ${max}.`;
  }
  if (!errors.arrivalWindowMins && hub.arrivalWindowMins > horizon)
    errors.arrivalWindowMins = "Arrival window must fit within the simulation horizon.";
  if (snapshot && !snapshot.hubs.some(h => h.id === hub.hubId)) errors.hubId = "Select a hub from this snapshot.";
  if (hub.repeatBookings && !snapshot?.bookingWindow) errors.repeatBookings = "Select a snapshot with booking data.";
  if (hub.disabled) {
    if (!Number.isInteger(hub.closeFromMins) || hub.closeFromMins < 0 || hub.closeFromMins >= horizon)
      errors.closeFromMins = "Closing minute must be within the simulation horizon.";
    if (hub.reopenAtMins !== null && (!Number.isInteger(hub.reopenAtMins) || hub.reopenAtMins <= hub.closeFromMins || hub.reopenAtMins > horizon))
      errors.reopenAtMins = "Reopening must be after closing and within the simulation horizon.";
  }
  return errors;
}

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
  return !!active && (scenario.name !== active.scenario.name || scenario.durationMins !== active.scenario.durationMins
    || scenario.seed !== active.scenario.seed || reuse !== active.id
    || JSON.stringify(hubSettings(scenario, repeatBookings)) !== JSON.stringify(hubSettings(active.scenario,
      active.snapshot.demandSource === "M2_BOOKING_VOLUME_REPEAT")));
}
