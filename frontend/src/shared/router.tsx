import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "./layout/AppLayout";
import { LoginPage } from "./layout/LoginPage";

// Moi module tu them route cua minh vao day; KHONG sua route cua module khac.
export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/m1/dashboard" replace /> },
      // M1
      { path: "m1/dashboard", lazy: async () => ({ Component: (await import("../modules/m1monitoring/pages/DashboardPage")).default }) },
      { path: "m1/hubs", lazy: async () => ({ Component: (await import("../modules/m1monitoring/pages/HubListPage")).default }) },
      { path: "m1/hubs/:hubId", lazy: async () => ({ Component: (await import("../modules/m1monitoring/pages/HubDetailPage")).default }) },
      { path: "m1/alerts", lazy: async () => ({ Component: (await import("../modules/m1monitoring/pages/AlertHistoryPage")).default }) },
      // M3 (module mau)
      { path: "m3/vehicles", lazy: async () => ({ Component: (await import("../modules/m3vehicle/pages/VehicleListPage")).default }) },
      // M2, M4..M8: moi nguoi them route tai day theo mau tren
    ],
  },
]);