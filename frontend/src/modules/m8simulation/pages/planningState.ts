import type { Run } from "../api/simulationApi";

export function refreshActiveRun(current: Run | undefined, saved: Run[]) {
  return current ? saved.find(run => run.id === current.id) ?? current : undefined;
}
export function acceptPlanningResult(current: Run | undefined, completed: Run) {
  return current?.id === completed.id ? completed : current;
}
