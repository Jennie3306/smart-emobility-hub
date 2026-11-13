import { createContext, useContext, useEffect, useState } from "react";
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { logout } from "../api/auth";

export type Role = "STUDENT" | "OPERATOR";
export interface CurrentUser { username: string; role: Role }

const UserContext = createContext<CurrentUser | null>(null);
/** The signed-in user, loaded once by the layout (null while loading). */
export const useCurrentUser = () => useContext(UserContext);

interface NavItem { to: string; label: string; code: string; roles: Role[] }

// Each module adds its own entry here; the server still decides what a role may do.
const NAV: NavItem[] = [
  { to: "/m1/dashboard", label: "Network dashboard", code: "M1", roles: ["OPERATOR"] },
  { to: "/m1/hubs", label: "Hubs", code: "M1", roles: ["STUDENT", "OPERATOR"] },
  { to: "/m1/alerts", label: "Alert history", code: "M1", roles: ["OPERATOR"] },
  { to: "/m2/search", label: "Find a vehicle", code: "M2", roles: ["STUDENT"] },
  { to: "/m2/my-reservation", label: "My reservation & trip", code: "M3", roles: ["STUDENT"] },
  { to: "/m3/vehicles", label: "Rental fleet", code: "M3", roles: ["OPERATOR"] },
  { to: "/m4/parking", label: "Parking", code: "M4", roles: ["STUDENT", "OPERATOR"] },
  { to: "/m5/charging", label: "Smart charging", code: "M5", roles: ["STUDENT", "OPERATOR"] },
  { to: "/m6/operations", label: "Incidents & IoT", code: "M6", roles: ["OPERATOR"] },
  { to: "/m7/redistribution", label: "Redistribution", code: "M7", roles: ["OPERATOR"] },
  { to: "/m8/simulation", label: "What-if Studio", code: "M8", roles: ["OPERATOR"] },
];

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
    api.get<CurrentUser>("/auth/me").then(r => setUser(r.data)).catch(() => navigate("/login"));
  }, [navigate]);

  const items = user ? NAV.filter(n => n.roles.includes(user.role)) : [];
  const current = NAV.find(n => location.pathname.startsWith(n.to));
  const initials = (user?.username ?? "").slice(0, 2).toUpperCase();

  async function signOut() {
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
            <aside className="relative flex h-full w-72 flex-col bg-brand-dark">
              <Brand />
              <SideNav items={items} onNavigate={() => setMenuOpen(false)} />
              <DemoNote />
            </aside>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-20 flex items-center gap-4 border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur md:px-8">
            <button type="button" className="btn btn-secondary btn-sm lg:hidden" aria-label="Open menu" onClick={() => setMenuOpen(true)}>☰</button>
            <div className="min-w-0">
              <p className="text-xs text-slate-500">Smart E-Mobility Hub <span className="px-1 text-slate-300">/</span> {current?.code ?? "Home"}</p>
              <p className="truncate text-sm font-semibold text-slate-900">{current?.label ?? "Workspace"}</p>
            </div>
            <div className="ml-auto flex items-center gap-3">
              {user && (
                <span className={`hidden rounded-full px-3 py-1 text-xs font-semibold sm:inline-flex ${user.role === "OPERATOR" ? "bg-orange-50 text-accent" : "bg-brand-100 text-brand"}`}>
                  {user.role === "OPERATOR" ? "Operator" : "Student"}
                </span>
              )}
              <span aria-hidden className="grid h-9 w-9 place-items-center rounded-full bg-brand text-xs font-bold text-white">{initials || "··"}</span>
              <button type="button" className="btn btn-secondary btn-sm" onClick={signOut}>Sign out</button>
            </div>
          </header>
          <main className="flex-1 px-4 py-6 md:px-8 md:py-8">
            <div className="mx-auto max-w-[1400px]"><Outlet /></div>
          </main>
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
