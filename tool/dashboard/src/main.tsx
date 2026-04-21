import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter, Route, Routes } from "react-router-dom"
import { registerCustomTheme } from "@pierre/diffs"

import "./index.css"
import App from "./App.tsx"
import { DataApp } from "@/features/data/data-app"
import { DesignSystemApp } from "@/features/design-system/design-system-app"

// Registered name must match the `name` field on the theme — this is
// what callers pass to `options.theme` on the diff components. The
// Islands Dark theme is a hand-rolled approximation of JetBrains' IDE
// scheme of the same name (see `src/themes/islands-dark.ts`).
registerCustomTheme("Islands Dark", () => import("./themes/islands-dark"))

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/design-system" element={<DesignSystemApp />} />
        <Route path="/data" element={<DataApp />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
