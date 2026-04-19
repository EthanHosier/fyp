import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter, Route, Routes } from "react-router-dom"

import "./index.css"
import App from "./App.tsx"
import { DataApp } from "@/features/data/data-app"
import { DesignSystemApp } from "@/features/design-system/design-system-app"

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
