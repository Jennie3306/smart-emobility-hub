import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./shared/router";
import { watchResponsiveTables } from "./shared/ui/responsiveTables";
import "./index.css";

watchResponsiveTables();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);
