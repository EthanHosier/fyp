import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter, Route, Routes } from "react-router-dom"
import { registerCustomTheme } from "@pierre/diffs"
import { WorkerPoolContextProvider } from "@pierre/diffs/react"

import "./index.css"
import App from "./App.tsx"
import { DataApp } from "@/features/data/data-app"
import { DesignSystemApp } from "@/features/design-system/design-system-app"
import { workerFactory } from "@/lib/worker-factory"

// Registered name must match the `name` field on the theme — this is
// what callers pass to `options.theme` on the diff components. The
// Islands Dark theme is a hand-rolled approximation of JetBrains' IDE
// scheme of the same name (see `src/themes/islands-dark.ts`).
registerCustomTheme("Islands Dark", () => import("./themes/islands-dark"))

// Worker-pool-driven highlighting. Moving Shiki off the main thread
// fixes the JCEF case where the per-component theme prop racing
// `registerCustomTheme`'s lazy import leaves diff cards rendered as
// plain (uncoloured) text. When the pool is active, the per-instance
// `theme` prop on `FileDiffCard` is ignored — the theme below is the
// single source of truth.
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <WorkerPoolContextProvider
      poolOptions={{ workerFactory }}
      highlighterOptions={{ theme: "Islands Dark" }}
    >
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/design-system" element={<DesignSystemApp />} />
          <Route path="/data" element={<DataApp />} />
        </Routes>
      </BrowserRouter>
    </WorkerPoolContextProvider>
  </StrictMode>,
)
