package com.github.ethanhosier.analysis.metrics.ck

import com.github.mauricioaniche.ck.CK
import com.github.mauricioaniche.ck.CKClassResult
import com.github.mauricioaniche.ck.CKNotifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs CK across a project root and returns a typed [CkResult].
 *
 * The CK library drives delivery via a [CKNotifier] callback: one `notify`
 * call per parsed class, one `notifyError` call per file CK couldn't handle.
 * We aggregate both streams into the result.
 */
class CkRunner {

    /**
     * @param projectDir root directory to scan recursively for `.java` files
     */
    fun run(projectDir: Path): CkResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }

        val classes = mutableListOf<CkClassMetrics>()
        val errors = mutableListOf<CkParseError>()
        val rootStr = projectDir.toAbsolutePath().normalize().toString()
        val start = System.currentTimeMillis()

        // CK constructor args match the library's `Runner` example:
        //   useJars = false      → don't pull in jar dependencies for type resolution
        //   maxAtOnce = 0        → no partitioning cap
        //   variablesAndFields = true → compute per-variable/field metrics (cheap enough)
        CK(false, 0, true).calculate(
            rootStr,
            object : CKNotifier {
                override fun notify(result: CKClassResult) {
                    classes += toMetrics(result, rootStr)
                }

                override fun notifyError(sourceFilePath: String, e: Exception) {
                    errors += CkParseError(
                        file = relativize(sourceFilePath, rootStr),
                        message = e.message ?: e::class.java.name,
                    )
                }
            },
        )

        return CkResult(
            perClass = classes,
            parseErrors = errors,
            durationMs = System.currentTimeMillis() - start,
        )
    }

    private fun toMetrics(r: CKClassResult, rootStr: String) = CkClassMetrics(
        className = r.className,
        file = relativize(r.file, rootStr),
        type = r.type,
        cbo = r.cbo,
        cboModified = r.cboModified,
        fanin = r.fanin,
        fanout = r.fanout,
        wmc = r.wmc,
        rfc = r.rfc,
        lcom = r.lcom,
        // CK returns NaN when a cohesion metric is undefined for the class
        // (e.g. < 2 eligible methods). Map NaN → null so we emit standard
        // JSON; downstream code can treat "undefined" distinctly from 0.
        lcomNormalized = r.lcomNormalized.takeIf { it.isFinite() },
        tcc = r.tightClassCohesion.takeIf { it.isFinite() },
        lcc = r.looseClassCohesion.takeIf { it.isFinite() },
        dit = r.dit,
        noc = r.noc,
        loc = r.loc,
        numberOfMethods = r.numberOfMethods,
        numberOfFields = r.numberOfFields,
        returnQty = r.returnQty,
        loopQty = r.loopQty,
        comparisonsQty = r.comparisonsQty,
        tryCatchQty = r.tryCatchQty,
        variablesQty = r.variablesQty,
        maxNestedBlocks = r.maxNestedBlocks,
        uniqueWordsQty = r.uniqueWordsQty,
    )

    private fun relativize(absPath: String, rootStr: String): String {
        if (absPath.startsWith(rootStr)) {
            return absPath.removePrefix(rootStr).trimStart('/', '\\')
        }
        return absPath
    }
}
