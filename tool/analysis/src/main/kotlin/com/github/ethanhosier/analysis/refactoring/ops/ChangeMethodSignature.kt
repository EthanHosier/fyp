package com.github.ethanhosier.analysis.refactoring.ops

import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import java.nio.file.Path

sealed interface SignatureParameter {
    data class Existing(
        val oldName: String,
        val newName: String? = null,
        val newType: String? = null,
    ) : SignatureParameter

    data class Added(
        val name: String,
        val type: String,
        val defaultValue: String,
    ) : SignatureParameter
}

data class ChangeMethodSignatureRequest(
    val projectRoot: Path,
    val sourceFolders: List<String>,
    val classpathJars: List<Path>,
    val declaringTypeFqn: String,
    val oldMethodName: String,
    // JDT-encoded param type signatures for overload disambiguation.
    val paramTypeSignatures: List<String>? = null,
    // Empty string leaves the name unchanged.
    val newMethodName: String = "",
    // Empty string leaves the return type unchanged.
    val newReturnType: String = "",
    // Target parameter list in desired order.
    val parameters: List<SignatureParameter>,
)

private val paramTypes: Array<Class<*>> = arrayOf(
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
    String::class.java,
    String::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
    Array<String>::class.java,
)

fun RefactoringClient.changeMethodSignature(req: ChangeMethodSignatureRequest): RefactoringOutcome {
    val n = req.parameters.size
    val kinds = Array(n) { "" }
    val oldNames = Array(n) { "" }
    val newNames = Array(n) { "" }
    val newTypes = Array(n) { "" }
    val defaults = Array(n) { "" }
    req.parameters.forEachIndexed { i, p ->
        when (p) {
            is SignatureParameter.Existing -> {
                kinds[i] = "existing"
                oldNames[i] = p.oldName
                newNames[i] = p.newName.orEmpty()
                newTypes[i] = p.newType.orEmpty()
            }
            is SignatureParameter.Added -> {
                kinds[i] = "added"
                newNames[i] = p.name
                newTypes[i] = p.type
                defaults[i] = p.defaultValue
            }
        }
    }
    return invokeOnBundle(
        "changeMethodSignature",
        paramTypes,
        arrayOf(
            req.projectRoot.toAbsolutePath().toString(),
            req.sourceFolders.toTypedArray(),
            req.classpathJars.map { it.toAbsolutePath().toString() }.toTypedArray(),
            req.declaringTypeFqn,
            req.oldMethodName,
            req.paramTypeSignatures?.toTypedArray(),
            req.newMethodName,
            req.newReturnType,
            kinds,
            oldNames,
            newNames,
            newTypes,
            defaults,
        ),
    )
}
