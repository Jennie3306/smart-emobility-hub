import axios from "axios";

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

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) window.location.href = "/login";
    return Promise.reject(err);
  }
);

/** Dang loi thong nhat tu backend: { code, message, details, timestamp } */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
