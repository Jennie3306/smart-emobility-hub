import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/auth";

export function LoginPage() {
  const [username, setUsername] = useState("operator1");
  const [password, setPassword] = useState("123456");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await login(username, password);
      navigate("/");
    } catch {
      setError("Invalid username or password");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen bg-canvas lg:grid-cols-[1.1fr_1fr]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-brand-dark p-12 text-white lg:flex">
        <div className="flex items-center gap-3">
          <span aria-hidden className="grid h-11 w-11 place-items-center rounded-xl bg-brand-light text-xl font-bold">↗</span>
          <div className="leading-tight">
            <p className="text-lg font-bold">e-Mobility Hub</p>
            <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-white/50">Smart campus network</p>
          </div>
        </div>
        <div className="max-w-md">
          <p className="eyebrow text-accent-light">HK261 · Software Engineering</p>
          <h1 className="mt-3 text-4xl font-bold leading-tight">One network of hubs, vehicles, parking and chargers.</h1>
          <p className="mt-4 text-white/70">Find and reserve a vehicle, book parking, charge by priority, and let operators see and coordinate the whole campus in real time.</p>
        </div>
        <p className="text-xs text-white/40">Demo environment · data are seeded and devices are simulated.</p>
      </section>

      <section className="flex items-center justify-center p-6">
        <form onSubmit={handleSubmit} className="card w-full max-w-sm space-y-5 p-8">
          <div>
            <h2 className="text-2xl font-bold text-slate-900">Sign in</h2>
            <p className="mt-1 text-sm text-slate-500">Use a demo account to continue.</p>
          </div>
          <label className="label">Username
            <input className="input" value={username} autoComplete="username"
                   onChange={(e) => setUsername(e.target.value)} placeholder="Username" />
          </label>
          <label className="label">Password
            <input className="input" type="password" value={password} autoComplete="current-password"
                   onChange={(e) => setPassword(e.target.value)} placeholder="Password" />
          </label>
          {error && <p role="alert" className="text-sm font-medium text-red-700">{error}</p>}
          <button type="submit" className="btn btn-primary w-full py-2.5" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
          <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
            <p className="font-semibold text-slate-700">Demo accounts (password 123456)</p>
            <p className="mt-1"><button type="button" className="link" onClick={() => setUsername("student1")}>student1</button> · student screens</p>
            <p><button type="button" className="link" onClick={() => setUsername("operator1")}>operator1</button> · operator screens, What-if Studio</p>
          </div>
        </form>
      </section>
    </div>
  );
}
