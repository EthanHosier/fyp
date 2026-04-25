package com.github.ethanhosier.analysis.miner.model

/**
 * In-memory typed payload describing a detected refactoring with enough
 * structured data to drive the corresponding [com.github.ethanhosier.analysis.refactoring.RefactoringClient]
 * operation. Carried alongside [DetectedRefactoring] on each
 * [RefactoringStep] via a `@Transient` field — never serialised, never
 * seen by the dashboard.
 *
 * Field shapes mirror the typed-request data classes under
 * `analysis/refactoring/ops/`, minus the project-environment fields
 * (`projectRoot`, `sourceFolders`, `classpathJars`) which
 * [com.github.ethanhosier.analysis.alternative.AlternativeTrajectoryRunner]
 * supplies at synthesis time.
 *
 * RM detections that aren't on the IDE-relevant allowlist, or IDE-relevant
 * types whose RM-typed mapper hasn't yet been implemented, surface as
 * [Other] and are skipped by the alternative-trajectory stage.
 */
sealed interface RefactoringSpec {

    data object Other : RefactoringSpec

    // ── Extract / Inline ────────────────────────────────────────────

    data class ExtractMethod(
        val relativeFilePath: String,
        val startLine: Int,
        val endLine: Int,
        val newMethodName: String,
    ) : RefactoringSpec

    data class InlineMethod(
        val declaringTypeFqn: String,
        val methodName: String,
        val paramTypeSignatures: List<String>? = null,
    ) : RefactoringSpec

    data class ExtractVariable(
        val relativeFilePath: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val newName: String,
    ) : RefactoringSpec

    data class InlineVariable(
        val relativeFilePath: String,
        val line: Int,
        val column: Int,
    ) : RefactoringSpec

    data class ExtractAttribute(
        val relativeFilePath: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val newName: String,
        val visibility: String = "private",
    ) : RefactoringSpec

    data class ExtractClass(
        val sourceTypeFqn: String,
        val newClassName: String,
        val delegateFieldName: String,
        val fieldNames: List<String>,
        val createGetterSetter: Boolean = false,
    ) : RefactoringSpec

    data class ExtractSubclass(
        val sourceTypeFqn: String,
        val newSubclassName: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    data class ExtractSuperclass(
        val sourceTypeFqn: String,
        val newSupertypeName: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    data class ExtractInterface(
        val sourceTypeFqn: String,
        val newInterfaceName: String,
        val methodNames: List<String>,
    ) : RefactoringSpec

    // ── Rename ──────────────────────────────────────────────────────

    data class RenameClass(
        val typeFqn: String,
        val newName: String,
    ) : RefactoringSpec

    data class RenameMethod(
        val declaringTypeFqn: String,
        val oldName: String,
        val newName: String,
        val paramTypeSignatures: List<String>? = null,
    ) : RefactoringSpec

    data class RenameField(
        val declaringTypeFqn: String,
        val oldName: String,
        val newName: String,
    ) : RefactoringSpec

    data class RenameLocalVariable(
        val relativeFilePath: String,
        val line: Int,
        val column: Int,
        val newName: String,
    ) : RefactoringSpec

    data class RenameParameter(
        val relativeFilePath: String,
        val line: Int,
        val column: Int,
        val newName: String,
    ) : RefactoringSpec

    data class RenamePackage(
        val oldPackage: String,
        val newPackage: String,
    ) : RefactoringSpec

    // ── Move ────────────────────────────────────────────────────────

    data class MoveClass(
        val typeFqn: String,
        val destinationPackage: String,
    ) : RefactoringSpec

    data class MoveInstanceMethod(
        val sourceTypeFqn: String,
        val methodName: String,
        val targetName: String,
    ) : RefactoringSpec

    data class MoveInstanceField(
        val sourceTypeFqn: String,
        val fieldName: String,
        val destinationTypeFqn: String,
    ) : RefactoringSpec

    data class MovePackage(
        val oldPackage: String,
        val newParentPackage: String,
    ) : RefactoringSpec

    data class MoveAndRenameClass(
        val typeFqn: String,
        val destinationPackage: String,
        val newName: String,
    ) : RefactoringSpec

    data class MoveAndRenameMethod(
        val sourceTypeFqn: String,
        val methodName: String,
        val targetName: String,
        val targetTypeFqn: String,
        val newMethodName: String,
    ) : RefactoringSpec

    data class MoveAndRenameAttribute(
        val sourceTypeFqn: String,
        val fieldName: String,
        val destinationTypeFqn: String,
        val newFieldName: String,
    ) : RefactoringSpec

    data class MoveStaticMembers(
        val sourceTypeFqn: String,
        val destinationTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    // ── Hierarchy ───────────────────────────────────────────────────

    data class PullUp(
        val declaringTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    data class PushDown(
        val declaringTypeFqn: String,
        val methodNames: List<String> = emptyList(),
        val fieldNames: List<String> = emptyList(),
    ) : RefactoringSpec

    data class ExtractAndMoveMethod(
        val relativeFilePath: String,
        val startLine: Int,
        val endLine: Int,
        val newMethodName: String,
        val declaringTypeFqn: String,
        val moveTargetName: String,
    ) : RefactoringSpec

    // ── Change Signature / Type ─────────────────────────────────────

    // Mirrors ops.ChangeMethodSignature.SignatureParameter structurally.
    sealed interface ChangeSignatureParameter {
        data class Existing(val oldName: String, val newName: String, val newType: String) : ChangeSignatureParameter
        data class Added(val name: String, val type: String, val defaultValue: String) : ChangeSignatureParameter
    }

    data class ChangeMethodSignature(
        val declaringTypeFqn: String,
        val oldMethodName: String,
        val paramTypeSignatures: List<String>? = null,
        val newMethodName: String = "",
        val newReturnType: String = "",
        val parameters: List<ChangeSignatureParameter>,
    ) : RefactoringSpec

    data class ChangeVariableType(
        val relativeFilePath: String,
        val line: Int,
        val column: Int,
        val newTypeFqn: String,
    ) : RefactoringSpec

    data class ChangeAttributeType(
        val declaringTypeFqn: String,
        val fieldName: String,
        val newTypeFqn: String,
    ) : RefactoringSpec

    data class ParameterizeVariable(
        val relativeFilePath: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val newParameterName: String,
    ) : RefactoringSpec

    data class ParameterizeAttribute(
        val relativeFilePath: String,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
        val newParameterName: String,
    ) : RefactoringSpec

    data class ReplaceVariableWithAttribute(
        val relativeFilePath: String,
        val line: Int,
        val column: Int,
        val newFieldName: String,
        val visibility: String = "private",
    ) : RefactoringSpec
}
