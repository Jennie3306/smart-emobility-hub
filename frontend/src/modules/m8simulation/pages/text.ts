/** "1 vehicle", "2 vehicles". Pass the plural form when it is not the singular plus "s". */
export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return count === 1 ? singular : pluralForm;
}

/** One line per run in History: how far its dispatch recommendations got. */
export function dispatchSummary(statuses: string[]): string {
  if (statuses.length === 0) return "No dispatch plan";
  const order = ["APPLIED", "APPROVED", "PARTIAL", "NEEDS_REVIEW", "PENDING", "DISCARDED"];
  const label: Record<string, string> = {
    APPLIED: "Completed in M7", APPROVED: "Approved in M7", PARTIAL: "Partly dispatched",
    NEEDS_REVIEW: "Needs review", PENDING: "Not applied", DISCARDED: "Discarded",
  };
  const top = order.find(status => statuses.includes(status)) ?? statuses[0];
  return label[top] ?? top;
}

/** Only same-site paths are accepted as a post-login destination. */
export function safeNext(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/login")) return "/";
  return next;
}
