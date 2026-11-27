import axios from "axios";
import { isHtmlPage, resolveApiBase } from "./server";

/**
 * Client goi API duy nhat cua he thong.
 * withCredentials: true la BAT BUOC - khong co thi trinh duyet khong gui JSESSIONID
 * va moi request sau dang nhap deu tra ve 401.
 */
export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

// In the Android app the backend address can change on the sign-in screen, so it is read per request.
api.interceptors.request.use((config) => {
  config.baseURL = resolveApiBase("/api");
  return config;
});

api.interceptors.response.use(
  (res) => {
    // Not the backend (e.g. the app's own page server): fail like an unreachable server.
    if (isHtmlPage(res.data)) return Promise.reject(new Error("Expected JSON from the API, got a web page"));
    return res;
  },
  (err) => {
    if (err.response?.status === 401 && !window.location.pathname.startsWith("/login")) {
      window.location.href = `/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`;
    }
    return Promise.reject(err);
  }
);

/** Dang loi thong nhat tu backend: { code, message, details, timestamp } */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
