import { api } from "./client";

export async function login(username: string, password: string) {
  const body = new URLSearchParams({ username, password });
  await api.post("/auth/login", body, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
}

export async function logout() {
  await api.post("/auth/logout");
}
