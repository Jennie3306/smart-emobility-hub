import { createContext, useContext, useEffect, useState } from "react";
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { logout } from "../api/auth";

export type Role = "STUDENT" | "OPERATOR";
export interface CurrentUser { username: string; role: Role }

const UserContext = createContext<CurrentUser | null>(null);
/** The signed-in user, loaded once by the layout (null while loading). */
export const useCurrentUser = () => useContext(UserContext);

interface NavItem {
  to: string; label: string; code: string; roles: Role[];
  /** Short label and icon for the phone tab bar. */
  short: string; icon: IconName;
  /** Shown in the phone tab bar for these roles; the rest stay in the menu. */
  tab?: Role[];
}

// Each module adds its own entry here; the server still decides what a role may do.
const NAV: NavItem[] = [
  { to: "/m1/dashboard", label: "Network dashboard", code: "M1", roles: ["OPERATOR"], short: "Dashboard", icon: "grid", tab: ["OPERATOR"] },
  { to: "/m1/hubs", label: "Hubs", code: "M1", roles: ["STUDENT", "OPERATOR"], short: "Hubs", icon: "pin", tab: ["STUDENT"] },
  { to: "/m1/alerts", label: "Alert history", code: "M1", roles: ["OPERATOR"], short: "Alerts", icon: "bell", tab: ["OPERATOR"] },
  { to: "/m2/search", label: "Find a vehicle", code: "M2", roles: ["STUDENT"], short: "Find", icon: "search", tab: ["STUDENT"] },
  { to: "/m2/my-reservation", label: "My reservation & trip", code: "M3", roles: ["STUDENT"], short: "My trip", icon: "ticket", tab: ["STUDENT"] },
  { to: "/m3/vehicles", label: "Rental fleet", code: "M3", roles: ["OPERATOR"], short: "Fleet", icon: "bike" },
  { to: "/m4/parking", label: "Parking", code: "M4", roles: ["STUDENT", "OPERATOR"], short: "Parking", icon: "parking", tab: ["STUDENT"] },
  { to: "/m5/charging", label: "Smart charging", code: "M5", roles: ["STUDENT", "OPERATOR"], short: "Charging", icon: "bolt", tab: ["STUDENT"] },
  { to: "/m6/operations", label: "Incidents & IoT", code: "M6", roles: ["OPERATOR"], short: "Incidents", icon: "wrench", tab: ["OPERATOR"] },
  { to: "/m7/redistribution", label: "Redistribution", code: "M7", roles: ["OPERATOR"], short: "Moves", icon: "swap", tab: ["OPERATOR"] },
  { to: "/m8/simulation", label: "What-if Studio", code: "M8", roles: ["OPERATOR"], short: "What-if", icon: "flask" },
];

type IconName = "grid" | "pin" | "bell" | "search" | "ticket" | "bike" | "parking" | "bolt" | "wrench" | "swap" | "flask" | "more";

const ICON_PATHS: Record<IconName, string> = {
  grid: "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z",
  pin: "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
  bell: "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0",
  search: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4",
  ticket: "M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4zM12 7v2M12 11v2M12 15v2",
  bike: "M6 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 15l4-7h5l3 7M9 8h3",
  parking: "M5 3h14v18H5zM10 17V7h3a3 3 0 0 1 0 6h-3",
  bolt: "M13 2 4 14h7l-1 8 9-12h-7z",
  wrench: "M14.5 6.5a4 4 0 0 0-5 5L4 17l3 3 5.5-5.5a4 4 0 0 0 5-5l-2.5 2.5-2.5-.5-.5-2.5z",
  swap: "M4 8h13l-3-3M20 16H7l3 3",
  flask: "M9 3h6M10 3v6l-5 9a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-9V3M7.5 14h9",
  more: "M5 12h.01M12 12h.01M19 12h.01",
};

function Icon({ name, className = "h-5 w-5" }: { name: IconName; className?: string }) {
  return (
    <svg aria-hidden viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={name === "more" ? 3.2 : 1.8}
      strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d={ICON_PATHS[name]} />
    </svg>
  );
}

function Brand() {
  return (
    <div className="flex items-center gap-3 px-5 py-6">
      <span aria-hidden className="grid h-10 w-10 place-items-center rounded-xl bg-brand-light text-lg font-bold text-white shadow-inner">↗</span>
      <div className="leading-tight">
        <p className="text-[15px] font-bold text-white">e-Mobility Hub</p>
        <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-white/50">Smart campus network</p>
      </div>
    </div>
  );
}

function SideNav({ items, onNavigate }: { items: NavItem[]; onNavigate?: () => void }) {
  return (
    <nav aria-label="Main" className="flex-1 overflow-y-auto px-3">
      <p className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-white/40">Workspace</p>
      <ul className="space-y-1">
        {items.map(n => (
          <li key={n.to}>
            <NavLink to={n.to} onClick={onNavigate}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${isActive ? "bg-accent text-white shadow" : "text-white/75 hover:bg-white/10 hover:text-white"}`}>
              <span className="grid h-6 w-7 shrink-0 place-items-center rounded-md bg-white/15 text-[10px] font-bold">{n.code}</span>
              <span className="truncate">{n.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function AccountBox({ user, onSignOut }: { user: CurrentUser | null; onSignOut: () => void }) {
  if (!user) return null;
  return (
    <div className="mx-4 mt-2 flex items-center gap-3 rounded-xl bg-white/10 p-3 text-white">
      <span aria-hidden className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-light text-xs font-bold">{user.username.slice(0, 2).toUpperCase()}</span>
      <div className="min-w-0 flex-1 leading-tight">
        <p className="truncate text-sm font-semibold">{user.username}</p>
        <p className="text-xs text-white/60">{user.role === "OPERATOR" ? "Operator" : "Student"}</p>
      </div>
      <button type="button" className="btn btn-secondary btn-sm" onClick={onSignOut}>Sign out</button>
    </div>
  );
}

/** Phone and tablet tab bar: the main screens of the role, one tap away (like a mobile app). */
function TabBar({ items, onMore }: { items: NavItem[]; onMore?: () => void }) {
  const cols = items.length + (onMore ? 1 : 0);
  const tab = "flex min-h-[56px] flex-col items-center justify-center gap-0.5 px-1 pt-1.5 text-[11px] font-medium leading-none";
  return (
    <nav aria-label="Main (mobile)"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_16px_rgba(15,23,42,0.06)] backdrop-blur lg:hidden">
      <ul className="mx-auto grid max-w-xl" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
        {items.map(n => (
          <li key={n.to}>
            <NavLink to={n.to}
              className={({ isActive }) => `${tab} relative ${isActive ? "text-brand" : "text-slate-500"}`}>
              {({ isActive }) => (<>
                {isActive && <span aria-hidden className="absolute inset-x-4 top-0 h-0.5 rounded-full bg-accent" />}
                <Icon name={n.icon} className="h-6 w-6" />
                <span className="truncate">{n.short}</span>
              </>)}
            </NavLink>
          </li>
        ))}
        {onMore && (
          <li><button type="button" className={`${tab} w-full text-slate-500`} onClick={onMore}><Icon name="more" className="h-6 w-6" /><span>More</span></button></li>
        )}
      </ul>
    </nav>
  );
}

function DemoNote() {
  return (
    <div className="m-4 rounded-xl border border-white/10 bg-white/5 p-3 text-xs text-white/70">
      <p className="flex items-center gap-2 font-semibold text-white"><span className="h-2 w-2 rounded-full bg-emerald-400" />Demo environment</p>
      <p className="mt-1">Seeded demo data and simulated IoT devices. HK261 · Software Engineering.</p>
    </div>
  );
}

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    api.get<CurrentUser>("/auth/me").then(r => setUser(r.data)).catch(() =>
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`));
    // Only the first load checks the session; later navigation keeps the user.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  const items = user ? NAV.filter(n => n.roles.includes(user.role)) : [];
  const tabs = user ? items.filter(n => n.tab?.includes(user.role)) : [];
  const hasMore = items.length > tabs.length;
  const current = NAV.find(n => location.pathname.startsWith(n.to));
  const initials = (user?.username ?? "").slice(0, 2).toUpperCase();

  async function signOut() {
    setMenuOpen(false);
    await logout().catch(() => undefined);
    navigate("/login");
  }

  return (
    <UserContext.Provider value={user}>
      <div className="app-shell flex min-h-screen bg-canvas">
        <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col bg-brand-dark lg:flex">
          <Brand />
          <SideNav items={items} />
          <DemoNote />
        </aside>

        {menuOpen && (
          <div className="fixed inset-0 z-30 lg:hidden" role="dialog" aria-modal="true">
            <button type="button" aria-label="Close menu" className="absolute inset-0 bg-slate-900/40" onClick={() => setMenuOpen(false)} />
            <aside className="relative flex h-full w-72 max-w-[85vw] flex-col bg-brand-dark pt-[env(safe-area-inset-top)]">
              <Brand />
              <SideNav items={items} onNavigate={() => setMenuOpen(false)} />
              <AccountBox user={user} onSignOut={signOut} />
              <DemoNote />
            </aside>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-slate-200 bg-white/90 px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] backdrop-blur md:gap-4 md:px-8">
            <button type="button" className="btn btn-secondary h-10 w-10 shrink-0 p-0 text-base lg:hidden" aria-label="Open menu" onClick={() => setMenuOpen(true)}>☰</button>
            <div className="min-w-0">
              <p className="hidden text-xs text-slate-500 sm:block">Smart E-Mobility Hub <span className="px-1 text-slate-300">/</span> {current?.code ?? "Home"}</p>
              <p className="truncate text-base font-semibold text-slate-900 sm:text-sm">{current?.label ?? "Workspace"}</p>
            </div>
            <div className="ml-auto flex items-center gap-3">
              {user && (
                <span className={`hidden rounded-full px-3 py-1 text-xs font-semibold sm:inline-flex ${user.role === "OPERATOR" ? "bg-orange-50 text-accent" : "bg-brand-100 text-brand"}`}>
                  {user.role === "OPERATOR" ? "Operator" : "Student"}
                </span>
              )}
              <span aria-hidden className="grid h-9 w-9 place-items-center rounded-full bg-brand text-xs font-bold text-white">{initials || "··"}</span>
              <button type="button" className="btn btn-secondary btn-sm hidden sm:inline-flex" onClick={signOut}>Sign out</button>
            </div>
          </header>
          <main className="flex-1 px-4 pb-[calc(6rem+env(safe-area-inset-bottom))] pt-5 md:px-8 md:pt-8 lg:pb-8">
            <div className="mx-auto max-w-[1400px]"><Outlet /></div>
          </main>
          {tabs.length > 0 && <TabBar items={tabs} onMore={hasMore ? () => setMenuOpen(true) : undefined} />}
        </div>
      </div>
    </UserContext.Provider>
  );
}

/** Start page: operators land on the dashboard, students on the hub list. */
export function RoleHome() {
  const user = useCurrentUser();
  if (!user) return null;
  return <Navigate to={user.role === "OPERATOR" ? "/m1/dashboard" : "/m1/hubs"} replace />;
}
