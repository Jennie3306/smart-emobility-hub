import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { logout } from "../api/auth";

const nav = [
  { to: "/m1/dashboard", label: "Dashboard (M1)" },
  { to: "/m3/vehicles",  label: "Rental vehicles (M3)" },
  // moi module tu them muc cua minh
  { to: "/m8/simulation", label: "What-if Studio (M8)" },
];

export function AppLayout() {
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-brand text-white px-6 py-3 flex items-center gap-6">
        <span className="font-bold">Smart E-Mobility Hub</span>
        <nav className="flex gap-4 text-sm">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to}
              className={({ isActive }) => (isActive ? "underline" : "opacity-80 hover:opacity-100")}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <button className="ml-auto text-sm opacity-80 hover:opacity-100" onClick={async () => { await logout(); navigate("/login"); }}>Sign out</button>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
