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

interface CapacitorGlobal { isNativePlatform?: () => boolean; getPlatform?: () => string; platform?: string }
interface NativeWindow {
  Capacitor?: CapacitorGlobal;
  androidBridge?: unknown;                                 // Capacitor's Android bridge
  webkit?: { messageHandlers?: { bridge?: unknown } };     // Capacitor's iOS bridge
}

/**
 * True only inside the installed app. The web bundle does not include @capacitor/core, so
 * window.Capacitor may be only the bare native bridge; the bridge objects are checked too.
 */
export function isNativeApp(): boolean {
  if (typeof window === "undefined") return false; // Node (unit tests): no browser, no app
  const w = window as unknown as NativeWindow;
  const cap = w.Capacitor;
  if (cap?.isNativePlatform?.() === true) return true;
  const platform = cap?.getPlatform?.() ?? cap?.platform;
  if (platform === "android" || platform === "ios") return true;
  return w.androidBridge !== undefined || w.webkit?.messageHandlers?.bridge !== undefined;
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

/**
 * Inside the app, every API call needs a backend address. Without one the request would
 * reach the WebView's own file server and come back as index.html, which pages would then
 * try to read as data. So: no address yet -> go to the sign-in screen, where it is entered.
 * Returns the base URL to use for `path`; throws when the request must not be sent.
 */
export function resolveApiBase(path: string): string {
  if (isNativeApp() && !serverUrl()) {
    if (!window.location.pathname.startsWith("/login")) window.location.href = "/login";
    throw new Error("No server address set in the app");
  }
  return apiUrl(path);
}

/** An HTML page where JSON was expected means the request never reached the backend. */
export function isHtmlPage(data: unknown): boolean {
  return typeof data === "string" && /^\s*<(!doctype html|html)/i.test(data);
}
