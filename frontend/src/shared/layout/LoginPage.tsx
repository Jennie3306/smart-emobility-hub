import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { safeNext } from "../api/safeNext";
import { login } from "../api/auth";
import { isNativeApp, serverUrl, setServerUrl } from "../api/server";

export function LoginPage() {
  const [username, setUsername] = useState("operator1");
  const [password, setPassword] = useState("123456");
  const [error, setError] = useState("");
  // Only the Android app asks for the backend address; the browser uses its own origin.
  const native = isNativeApp();
  const [server, setServer] = useState(() => serverUrl());
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const [params] = useSearchParams();

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    if (native) setServerUrl(server);
    try {
      await login(username, password);
      navigate(safeNext(params.get("next")), { replace: true });
    } catch (e) {
      const unreachable = !(e as { response?: unknown }).response;
      setError(unreachable
        ? native ? `Cannot reach the server at ${serverUrl() || "(no address)"}. Check the address and that the phone is on the same Wi-Fi.` : "Cannot reach the server. Is the backend running?"
        : "Invalid username or password");
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

      <section className="flex flex-col items-center justify-center gap-6 p-6 pt-[max(1.5rem,env(safe-area-inset-top))]">
        <div className="flex items-center gap-3 lg:hidden">
          <span aria-hidden className="grid h-11 w-11 place-items-center rounded-xl bg-brand text-xl font-bold text-white">↗</span>
          <div className="leading-tight">
            <p className="text-lg font-bold text-slate-900">e-Mobility Hub</p>
            <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-500">Smart campus network</p>
          </div>
        </div>
        <form onSubmit={handleSubmit} className="card w-full max-w-sm space-y-5 p-6 sm:p-8">
          <div>
            <h2 className="text-2xl font-bold text-slate-900">Sign in</h2>
            <p className="mt-1 text-sm text-slate-500">Use a demo account to continue.</p>
          </div>
          {native && (
            <label className="label">Server address
              <input className="input" value={server} inputMode="url" autoCapitalize="none" autoCorrect="off" spellCheck={false}
                     required placeholder="http://192.168.1.20:8080" onChange={(e) => setServer(e.target.value)} />
              <span className="hint mt-1 block">The laptop that runs the backend, on the same Wi-Fi as this phone.</span>
            </label>
          )}
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
