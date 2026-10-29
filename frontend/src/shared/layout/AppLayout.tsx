import { NavLink, Outlet } from "react-router-dom";

const nav = [
  { to: "/m1/dashboard", label: "Dashboard (M1)" },
  { to: "/m3/vehicles",  label: "Xe thue (M3)" },
  { to: "/m6/operations", label: "Sự cố & IoT (M6)" },
  // moi module tu them muc cua minh
];

export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-brand text-white px-6 py-3 flex flex-wrap items-center gap-4">
        <span className="font-bold">Smart E-Mobility Hub</span>
        <nav className="flex flex-wrap gap-4 text-sm">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to}
              className={({ isActive }) => (isActive ? "underline" : "opacity-80 hover:opacity-100")}>
              {n.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="p-4 md:p-6">
        <Outlet />
      </main>
    </div>
  );
}
