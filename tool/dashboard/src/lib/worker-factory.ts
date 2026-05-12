// Vite-style worker factory for @pierre/diffs's worker pool. The
// `?worker&url` suffix asks Vite to return the worker bundle's URL as
// a string, which `new Worker` then loads as an ES-module worker.
// Loading highlighting off the main thread sidesteps the JCEF-vs-main-
// thread-Shiki-init race that drops syntax colours from the diff cards.
import WorkerUrl from "@pierre/diffs/worker/worker.js?worker&url"

export function workerFactory(): Worker {
  return new Worker(WorkerUrl, { type: "module" })
}
