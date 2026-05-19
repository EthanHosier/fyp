import fs from "fs"
import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig, type Plugin } from "vite"

// Allow `REFDASH_REPORT=/path/to/analysis-report.json npm run dev`.
// When the env var is set, a dev-only middleware serves that file at
// `/__dev_report.json`; useReport.ts fetches that URL in dev mode and
// short-circuits the plugin-injected `window.__REPORT__` path.
//
// We use an env var rather than a CLI flag because vite's CLI parser
// (CAC) rejects unknown options like `--report=...`.
function parseReportArg(): string | null {
  const p = process.env.REFDASH_REPORT
  if (!p) return null
  return path.isAbsolute(p) ? p : path.resolve(process.cwd(), p)
}

function devReportPlugin(reportPath: string): Plugin {
  return {
    name: "refdash-dev-report",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use("/__dev_report.json", (_req, res) => {
        try {
          const body = fs.readFileSync(reportPath)
          res.setHeader("content-type", "application/json")
          res.setHeader("cache-control", "no-store")
          res.end(body)
        } catch (e) {
          res.statusCode = 500
          res.end(String(e))
        }
      })
      server.config.logger.info(
        `[refdash] dev report: serving ${reportPath} at /__dev_report.json`,
      )
    },
  }
}

const reportPath = parseReportArg()

// https://vite.dev/config/
export default defineConfig({
  // Relative asset paths so the built bundle works when loaded through the
  // plugin's custom `refdash://app/...` scheme (absolute paths would resolve
  // against the scheme root, not the document's directory).
  base: "./",
  plugins: [
    react({
      babel: {
        plugins: ["babel-plugin-react-compiler"],
      },
    }),
    tailwindcss(),
    ...(reportPath ? [devReportPlugin(reportPath)] : []),
  ],
  define: {
    __DEV_REPORT_AVAILABLE__: JSON.stringify(reportPath != null),
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
