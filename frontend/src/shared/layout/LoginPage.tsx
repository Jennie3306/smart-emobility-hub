import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/auth";

export function LoginPage() {
  const [username, setUsername] = useState("student1");
  const [password, setPassword] = useState("123456");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  async function handleSubmit() {
    try {
      await login(username, password);
      navigate("/");
    } catch {
      setError("Sai tai khoan hoac mat khau");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="bg-white p-8 rounded-lg shadow w-96 space-y-4">
        <h1 className="text-xl font-bold text-brand">Dang nhap</h1>
        <input className="w-full border rounded px-3 py-2" value={username}
               onChange={(e) => setUsername(e.target.value)} placeholder="Tai khoan" />
        <input className="w-full border rounded px-3 py-2" type="password" value={password}
               onChange={(e) => setPassword(e.target.value)} placeholder="Mat khau" />
        {error && <p className="text-state-fault text-sm">{error}</p>}
        <button className="w-full bg-brand text-white rounded py-2" onClick={handleSubmit}>
          Dang nhap
        </button>
      </div>
    </div>
  );
}
