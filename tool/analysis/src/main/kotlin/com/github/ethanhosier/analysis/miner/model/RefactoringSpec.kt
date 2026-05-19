package com.github.ethanhosier.analysis.miner.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed payload describing a detected refactoring with enough structured
 * data to drive the corresponding
 * [com.github.ethanhosier.analysis.refactoring.RefactoringClient] op.
 *
 * Carried two ways:
 *  1. **Transient** on each [RefactoringStep] (`@Transient val spec`)
 *     — used by `AlternativeTrajectoryRunner`. Excluded from the
 *     `refactoringSteps[]` JSON to keep that array byte-stable.
 *  2. **Serialised** on each
 *     [com.github.ethanhosier.analysis.metrics.model.AlternativeTrajectory]
 *     — surfaces in the report so the frontend knows exactly which IDE
 *     refactoring was synthesised and with what parameters.
 *
 * Field shapes mirror the typed-request data classes under
 * `analysis/refactoring/ops/`, minus the project-environment fields
 * (`projectRoot`, `sourceFolders`, `classpathJars`) which
 * `AlternativeTrajectoryRunner` supplies at synthesis time.
 *
 * RM detections that aren't on the IDE-relevant allowlist, or IDE-relevant
 * types whose RM-typed mapper hasn't yet been implemented, surface as
 * [Other] and are skipped by the alternative-trajectory stage.
 */
@Serializable
sealed interface RefactoringSpec {

    @Serializable @SerialName("Other")
    data object Other : RefactoringSpec

    // ── Extract / Inline ────────────────────────────────────────────

    @Serializable @SerialName("ExtractMethod")
    data class ExtractMethod(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newMethodName: String,
        /** True iff the user's resulting method declaration carries
         *  `static`. JDT's `ExtractMethodRefactoring` only adds the
         *  modifier when it's *required*; IntelliJ's heuristic adds
         *  it whenever the body permits. We post-process the result
         *  to add `static` if this is set, so our reapplication
         *  matches whatever the user actually did. */
        val isStatic: Boolean = false,
    ) : RefactoringSpec

    @Serializable @SerialName("InlineMethod")
    data class InlineMethod(
        val declaringTypeFqn: String,
        val methodName: String,
        val paramTypeSignatures: List<String>? = null,
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractVariable")
    data class ExtractVariable(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("InlineVariable")
    data class InlineVariable(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractAttribute")
    data class ExtractAttribute(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newName: String,
        val visibility: String = "private",
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractClass")
    data class ExtractClass(
        val sourceTypeFqn: String,
        val newClassName: String,
        val delegateFieldName: String,
        val fieldNames: List<String>,
        val createGetterSetter: Boolean = false,
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractSubclass")
    data class ExtractSubclass(
        val sourceTypeFqn: String,
        val newSubclassName: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractSuperclass")
    data class ExtractSuperclass(
        val sourceTypeFqn: String,
        val newSupertypeName: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractInterface")
    data class ExtractInterface(
        val sourceTypeFqn: String,
        val newInterfaceName: String,
        val methodNames: List<String>,
    ) : RefactoringSpec

    // ── Rename ──────────────────────────────────────────────────────

    @Serializable @SerialName("RenameClass")
    data class RenameClass(
        val typeFqn: String,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("RenameMethod")
    data class RenameMethod(
        val declaringTypeFqn: String,
        val oldName: String,
        val newName: String,
        val paramTypeSignatures: List<String>? = null,
    ) : RefactoringSpec

    @Serializable @SerialName("RenameField")
    data class RenameField(
        val declaringTypeFqn: String,
        val oldName: String,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("RenameLocalVariable")
    data class RenameLocalVariable(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("RenameParameter")
    data class RenameParameter(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("RenamePackage")
    data class RenamePackage(
        val oldPackage: String,
        val newPackage: String,
    ) : RefactoringSpec

    // ── Move ────────────────────────────────────────────────────────

    @Serializable @SerialName("MoveClass")
    data class MoveClass(
        val typeFqn: String,
        val destinationPackage: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveInstanceMethod")
    data class MoveInstanceMethod(
        val sourceTypeFqn: String,
        val methodName: String,
        val targetName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveInstanceField")
    data class MoveInstanceField(
        val sourceTypeFqn: String,
        val fieldName: String,
        val destinationTypeFqn: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MovePackage")
    data class MovePackage(
        val oldPackage: String,
        val newParentPackage: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveAndRenameClass")
    data class MoveAndRenameClass(
        val typeFqn: String,
        val destinationPackage: String,
        val newName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveAndRenameMethod")
    data class MoveAndRenameMethod(
        val sourceTypeFqn: String,
        val methodName: String,
        val targetName: String,
        val targetTypeFqn: String,
        val newMethodName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveAndRenameAttribute")
    data class MoveAndRenameAttribute(
        val sourceTypeFqn: String,
        val fieldName: String,
        val destinationTypeFqn: String,
        val newFieldName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("MoveStaticMembers")
    data class MoveStaticMembers(
        val sourceTypeFqn: String,
        val destinationTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    // ── Hierarchy ───────────────────────────────────────────────────

    @Serializable @SerialName("PullUp")
    data class PullUp(
        val declaringTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    @Serializable @SerialName("PushDown")
    data class PushDown(
        val declaringTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    @Serializable @SerialName("ExtractAndMoveMethod")
    data class ExtractAndMoveMethod(
        val relativeFilePath: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val newMethodName: String,
        val declaringTypeFqn: String,
        val moveTargetName: String,
    ) : RefactoringSpec

    // ── Change Signature / Type ─────────────────────────────────────

    // Mirrors ops.ChangeMethodSignature.SignatureParameter structurally.
    @Serializable
    sealed interface ChangeSignatureParameter {
        @Serializable @SerialName("Existing")
        data class Existing(val oldName: String, val newName: String, val newType: String) : ChangeSignatureParameter
        @Serializable @SerialName("Added")
        data class Added(
            val name: String,
            // Renamed in JSON to avoid colliding with kotlinx.serialization's
            // default sealed-class discriminator ("type"). Kept as `type` in
            // Kotlin so existing call sites compile unchanged.
            @SerialName("paramType") val type: String,
            val defaultValue: String,
        ) : ChangeSignatureParameter
    }

    @Serializable @SerialName("ChangeMethodSignature")
    data class ChangeMethodSignature(
        val declaringTypeFqn: String,
        val oldMethodName: String,
        val paramTypeSignatures: List<String>? = null,
        val newMethodName: String = "",
        val newReturnType: String = "",
        val parameters: List<ChangeSignatureParameter>,
    ) : RefactoringSpec

    @Serializable @SerialName("ChangeVariableType")
    data class ChangeVariableType(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newTypeFqn: String,
    ) : RefactoringSpec

    @Serializable @SerialName("ChangeAttributeType")
    data class ChangeAttributeType(
        val declaringTypeFqn: String,
        val fieldName: String,
        val newTypeFqn: String,
    ) : RefactoringSpec

    @Serializable @SerialName("ParameterizeVariable")
    data class ParameterizeVariable(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newParameterName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("ParameterizeAttribute")
    data class ParameterizeAttribute(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val selectionSubtreeHash: String,
        val selectionNodeCount: Int,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newParameterName: String,
    ) : RefactoringSpec

    @Serializable @SerialName("ReplaceVariableWithAttribute")
    data class ReplaceVariableWithAttribute(
        val relativeFilePath: String,
        val declaringTypeFqn: String,
        val hostMethodName: String,
        val hostMethodParamTypes: List<String>,
        val declarationSubtreeHash: String,
        val originalLineHint: Int? = null,
        val originalColumnHint: Int? = null,
        val newFieldName: String,
        val visibility: String = "private",
    ) : RefactoringSpec
}
