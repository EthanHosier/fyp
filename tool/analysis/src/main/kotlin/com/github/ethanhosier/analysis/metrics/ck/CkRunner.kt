package com.github.ethanhosier.analysis.metrics.ck

import com.github.mauricioaniche.ck.CK
import com.github.mauricioaniche.ck.CKClassResult
import com.github.mauricioaniche.ck.CKNotifier
import java.nio.file.Files
import java.nio.file.Path

class CkRunner {

    fun run(projectDir: Path): CkResult {
        require(Files.isDirectory(projectDir)) { "not a directory: $projectDir" }

        val classes = mutableListOf<CkClassMetrics>()
        val errors = mutableListOf<CkParseError>()
        val rootStr = projectDir.toAbsolutePath().normalize().toString()
        val start = System.currentTimeMillis()

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
