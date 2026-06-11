package com.github.ethanhosier.analysis.alternative.validate

import com.github.ethanhosier.analysis.miner.model.RefactoringSpec
import com.github.ethanhosier.analysis.refactoring.RefactoringClient
import com.github.ethanhosier.analysis.refactoring.RefactoringOutcome
import com.github.ethanhosier.analysis.refactoring.ops.ChangeAttributeTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeMethodSignatureRequest
import com.github.ethanhosier.analysis.refactoring.ops.ChangeVariableTypeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractInterfaceRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractSuperclassRequest
import com.github.ethanhosier.analysis.refactoring.ops.ExtractVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.InlineVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveAndRenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.MoveInstanceFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.ParameterizeVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.PullUpRequest
import com.github.ethanhosier.analysis.refactoring.ops.PushDownRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameClassRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameFieldRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameLocalVariableRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenameMethodRequest
import com.github.ethanhosier.analysis.refactoring.ops.RenamePackageRequest
import com.github.ethanhosier.analysis.refactoring.ops.ReplaceVariableWithAttributeRequest
import com.github.ethanhosier.analysis.refactoring.ops.SignatureParameter
import com.github.ethanhosier.analysis.refactoring.ops.changeAttributeType
import com.github.ethanhosier.analysis.refactoring.ops.changeMethodSignature
import com.github.ethanhosier.analysis.refactoring.ops.changeVariableType
import com.github.ethanhosier.analysis.refactoring.ops.extractAttribute
import com.github.ethanhosier.analysis.refactoring.ops.extractClass
import com.github.ethanhosier.analysis.refactoring.ops.extractInterface
import com.github.ethanhosier.analysis.refactoring.ops.extractMethod
import com.github.ethanhosier.analysis.refactoring.ops.extractSuperclass
import com.github.ethanhosier.analysis.refactoring.ops.extractVariable
import com.github.ethanhosier.analysis.refactoring.ops.inlineMethod
import com.github.ethanhosier.analysis.refactoring.ops.inlineVariable
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameAttribute
import com.github.ethanhosier.analysis.refactoring.ops.moveAndRenameClass
import com.github.ethanhosier.analysis.refactoring.ops.moveClass
import com.github.ethanhosier.analysis.refactoring.ops.moveInstanceField
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeAttribute
import com.github.ethanhosier.analysis.refactoring.ops.parameterizeVariable
import com.github.ethanhosier.analysis.refactoring.ops.pullUp
import com.github.ethanhosier.analysis.refactoring.ops.pushDown
import com.github.ethanhosier.analysis.refactoring.ops.renameClass
import com.github.ethanhosier.analysis.refactoring.ops.renameField
import com.github.ethanhosier.analysis.refactoring.ops.renameLocalVariable
import com.github.ethanhosier.analysis.refactoring.ops.renameMethod
import com.github.ethanhosier.analysis.refactoring.ops.renamePackage
import com.github.ethanhosier.analysis.refactoring.ops.replaceVariableWithAttribute
import java.nio.file.Path

class SpecDispatcher(
    private val client: RefactoringClient,
    private val sourceFolders: List<String> = listOf("src/main/java"),
    private val classpathJars: List<Path> = emptyList(),
) {

    sealed interface Result {
        data object Ok : Result
        data class Failed(val reason: String) : Result
    }

    fun apply(spec: RefactoringSpec, worktree: Path): Result {
        val outcome: RefactoringOutcome = when (spec) {
            is RefactoringSpec.RenameMethod -> client.renameMethod(
                RenameMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldName = spec.oldName,
                    newName = spec.newName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                ),
            )

            is RefactoringSpec.RenameClass -> client.renameClass(
                RenameClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.RenameField -> client.renameField(
                RenameFieldRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldName = spec.oldName,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.RenamePackage -> client.renamePackage(
                RenamePackageRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    oldPackage = spec.oldPackage,
                    newPackage = spec.newPackage,
                ),
            )

            is RefactoringSpec.MoveClass -> client.moveClass(
                MoveClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    destinationPackage = spec.destinationPackage,
                ),
            )

            is RefactoringSpec.MoveAndRenameClass -> client.moveAndRenameClass(
                MoveAndRenameClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    typeFqn = spec.typeFqn,
                    destinationPackage = spec.destinationPackage,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.MoveInstanceField -> client.moveInstanceField(
                MoveInstanceFieldRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    fieldName = spec.fieldName,
                    destinationTypeFqn = spec.destinationTypeFqn,
                ),
            )

            is RefactoringSpec.MoveAndRenameAttribute -> client.moveAndRenameAttribute(
                MoveAndRenameAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    fieldName = spec.fieldName,
                    destinationTypeFqn = spec.destinationTypeFqn,
                    newFieldName = spec.newFieldName,
                ),
            )

            is RefactoringSpec.PullUp -> client.pullUp(
                PullUpRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.PushDown -> client.pushDown(
                PushDownRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.ExtractMethod -> client.extractMethod(
                ExtractMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    selectionSubtreeHash = spec.selectionSubtreeHash,
                    selectionNodeCount = spec.selectionNodeCount,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newMethodName = spec.newMethodName,
                    isStatic = spec.isStatic,
                ),
            )

            is RefactoringSpec.InlineMethod -> client.inlineMethod(
                InlineMethodRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    methodName = spec.methodName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                ),
            )

            is RefactoringSpec.ExtractVariable -> client.extractVariable(
                ExtractVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    selectionSubtreeHash = spec.selectionSubtreeHash,
                    selectionNodeCount = spec.selectionNodeCount,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.InlineVariable -> client.inlineVariable(
                InlineVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    declarationSubtreeHash = spec.declarationSubtreeHash,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                ),
            )

            is RefactoringSpec.ExtractAttribute -> client.extractAttribute(
                ExtractAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    selectionSubtreeHash = spec.selectionSubtreeHash,
                    selectionNodeCount = spec.selectionNodeCount,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newName = spec.newName,
                    visibility = spec.visibility,
                ),
            )

            is RefactoringSpec.ChangeVariableType -> client.changeVariableType(
                ChangeVariableTypeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    declarationSubtreeHash = spec.declarationSubtreeHash,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newTypeFqn = spec.newTypeFqn,
                ),
            )

            is RefactoringSpec.ChangeAttributeType -> client.changeAttributeType(
                ChangeAttributeTypeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    fieldName = spec.fieldName,
                    newTypeFqn = spec.newTypeFqn,
                ),
            )

            is RefactoringSpec.RenameLocalVariable -> client.renameLocalVariable(
                RenameLocalVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    declarationSubtreeHash = spec.declarationSubtreeHash,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.RenameParameter -> client.renameLocalVariable(
                RenameLocalVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    declarationSubtreeHash = spec.declarationSubtreeHash,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newName = spec.newName,
                ),
            )

            is RefactoringSpec.ParameterizeVariable -> client.parameterizeVariable(
                ParameterizeVariableRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    selectionSubtreeHash = spec.selectionSubtreeHash,
                    selectionNodeCount = spec.selectionNodeCount,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newParameterName = spec.newParameterName,
                ),
            )

            is RefactoringSpec.ParameterizeAttribute -> client.parameterizeAttribute(
                ParameterizeAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    selectionSubtreeHash = spec.selectionSubtreeHash,
                    selectionNodeCount = spec.selectionNodeCount,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newParameterName = spec.newParameterName,
                ),
            )

            is RefactoringSpec.ReplaceVariableWithAttribute -> client.replaceVariableWithAttribute(
                ReplaceVariableWithAttributeRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    relativeFilePath = spec.relativeFilePath,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    hostMethodName = spec.hostMethodName,
                    hostMethodParamTypes = spec.hostMethodParamTypes,
                    declarationSubtreeHash = spec.declarationSubtreeHash,
                    originalLineHint = spec.originalLineHint,
                    originalColumnHint = spec.originalColumnHint,
                    newFieldName = spec.newFieldName,
                    visibility = spec.visibility,
                ),
            )

            is RefactoringSpec.ChangeMethodSignature -> client.changeMethodSignature(
                ChangeMethodSignatureRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    declaringTypeFqn = spec.declaringTypeFqn,
                    oldMethodName = spec.oldMethodName,
                    paramTypeSignatures = spec.paramTypeSignatures,
                    newMethodName = spec.newMethodName,
                    newReturnType = spec.newReturnType,
                    parameters = spec.parameters.map { it.toOpsParameter() },
                ),
            )

            is RefactoringSpec.ExtractSuperclass -> client.extractSuperclass(
                ExtractSuperclassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newSupertypeName = spec.newSupertypeName,
                    methodNames = spec.methodNames,
                    fieldNames = spec.fieldNames,
                ),
            )

            is RefactoringSpec.ExtractClass -> client.extractClass(
                ExtractClassRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newClassName = spec.newClassName,
                    delegateFieldName = spec.delegateFieldName,
                    fieldNames = spec.fieldNames,
                    createGetterSetter = spec.createGetterSetter,
                ),
            )

            is RefactoringSpec.ExtractInterface -> client.extractInterface(
                ExtractInterfaceRequest(
                    projectRoot = worktree,
                    sourceFolders = sourceFolders,
                    classpathJars = classpathJars,
                    sourceTypeFqn = spec.sourceTypeFqn,
                    newInterfaceName = spec.newInterfaceName,
                    methodNames = spec.methodNames,
                ),
            )

            else -> return Result.Failed(
                "dispatch arm not implemented for ${spec::class.simpleName}",
            )
        }
        return when (outcome) {
            is RefactoringOutcome.Success -> Result.Ok
            is RefactoringOutcome.Failed -> Result.Failed("RefactoringClient: ${outcome.reason}")
        }
    }

    private fun RefactoringSpec.ChangeSignatureParameter.toOpsParameter(): SignatureParameter = when (this) {
        is RefactoringSpec.ChangeSignatureParameter.Existing -> SignatureParameter.Existing(
            oldName = oldName,
            newName = newName,
            newType = newType,
        )
        is RefactoringSpec.ChangeSignatureParameter.Added -> SignatureParameter.Added(
            name = name,
            type = type,
            defaultValue = defaultValue,
        )
    }
}
