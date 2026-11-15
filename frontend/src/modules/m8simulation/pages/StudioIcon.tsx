export function StudioIcon({ kind }: { kind: "scenario" | "history" | "network" | "refresh" | "arrow" }) {
  const paths = {
    scenario: <><rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h3"/></>,
    history: <><path d="M3 11a9 9 0 1 1 2 7M3 5v6h6"/><path d="M12 7v5l3 2"/></>,
    network: <><rect x="9" y="2" width="6" height="5" rx="1"/><rect x="2" y="17" width="6" height="5" rx="1"/><rect x="16" y="17" width="6" height="5" rx="1"/><path d="M12 7v5M5 17v-5h14v5"/></>,
    refresh: <><path d="M20 8a8 8 0 0 0-14-3L3 8m0-5v5h5M4 16a8 8 0 0 0 14 3l3-3m0 5v-5h-5"/></>,
    arrow: <path d="M5 19 19 5M5 5h14v14"/>,
  };
  return <svg className="m8-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[kind]}</svg>;
}
