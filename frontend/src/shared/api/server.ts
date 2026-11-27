/**
 * Where the backend is.
 *
 * In a browser the page and the API share one origin (nginx in the demo, the Vite proxy in
 * development), so every URL stays relative: "/api/...", "/ws".
 *
 * Inside the Android app (Capacitor, see mobile/README.md) the page is loaded from the phone
 * itself, so the API needs the full address of the backend, e.g. "http://192.168.1.20:8080".
 * The user enters it once on the sign-in screen; it is kept on the phone.
 */
const KEY = "emh.serverUrl";

interface CapacitorGlobal { isNativePlatform?: () => boolean }

/** True only inside the installed app (the Capacitor runtime injects window.Capacitor). */
export function isNativeApp(): boolean {
  if (typeof window === "undefined") return false; // Node (unit tests): no browser, no app
  const cap = (window as unknown as { Capacitor?: CapacitorGlobal }).Capacitor;
  return cap?.isNativePlatform?.() === true;
}

function normalise(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, "");
  if (!trimmed) return "";
  return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
}

/** Backend address chosen in the app; "" in the browser (same origin). */
export function serverUrl(): string {
  if (!isNativeApp()) return "";
  let stored: string | null = null;
  try { stored = localStorage.getItem(KEY); } catch { /* storage unavailable */ }
  return normalise(stored ?? import.meta.env.VITE_SERVER_URL ?? "");
}

export function setServerUrl(url: string): void {
  try { localStorage.setItem(KEY, normalise(url)); } catch { /* storage unavailable */ }
}

/** "/api/m1" -> "/api/m1" in the browser, "http://host:8080/api/m1" in the app. */
export function apiUrl(path: string): string {
  return serverUrl() + path;
}
