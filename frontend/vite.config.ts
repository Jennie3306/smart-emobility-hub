import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [react()],
  // SockJS references the Node global alias in its browser bundle.
  define: { global: "globalThis" },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // CORS allows only this origin: fail instead of silently moving to 5174 (login would get 403).
    strictPort: true,
    proxy: {
      // goi /api/... tu FE se duoc chuyen sang backend, cookie giu nguyen
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      "/ws":  { target: "http://localhost:8080", changeOrigin: true, ws: true },
    },
  },
});
