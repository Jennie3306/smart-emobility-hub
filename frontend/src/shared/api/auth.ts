import { api } from "./client";

export async function login(username: string, password: string) {
  const body = new URLSearchParams({ username, password });
  await api.post("/auth/login", body, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  return api.get<{ username: string; role: string }>("/auth/me").then(r => r.data);
}

export async function logout() {
  await api.post("/auth/logout");
}
