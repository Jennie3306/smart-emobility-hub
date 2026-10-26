import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/auth";

export function LoginPage() {
  const [username, setUsername] = useState("operator1");
  const [password, setPassword] = useState("123456");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  async function handleSubmit() {
    try {
      const user = await login(username, password);
      navigate(user.role === "OPERATOR" ? "/m8/simulation" : "/m2/search");
    } catch {
      setError("Invalid username or password");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="bg-white p-8 rounded-lg shadow w-96 space-y-4">
        <h1 className="text-xl font-bold text-brand">Sign in</h1>
        <p className="text-sm text-slate-600">What-if Studio requires an Operator account.</p>
        <p className="text-xs text-slate-500">Demo: operator1 / 123456. Student accounts cannot access M8.</p>
        <input className="w-full border rounded px-3 py-2" value={username}
               onChange={(e) => setUsername(e.target.value)} placeholder="Username" />
        <input className="w-full border rounded px-3 py-2" type="password" value={password}
               onChange={(e) => setPassword(e.target.value)} placeholder="Password" />
        {error && <p className="text-state-fault text-sm">{error}</p>}
        <button className="w-full bg-brand text-white rounded py-2" onClick={handleSubmit}>
          Sign in
        </button>
      </div>
    </div>
  );
}
