import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter, Route, Routes } from "react-router-dom"

import "./index.css"
import App from "./App.tsx"
import { PreviewApp } from "@/features/preview/preview-app"

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/preview" element={<PreviewApp />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
