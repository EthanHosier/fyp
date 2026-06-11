package com.github.ethanhosier.refactoringbundle.internal.ops

import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring

internal object ChangeMethodSignatureOp {

    fun run(
        javaProject: IJavaProject,
        declaringTypeFqn: String,
        oldMethodName: String,
        paramTypeSignatures: Array<String>?,
        newMethodName: String,
        newReturnType: String,
        paramKinds: Array<String>,
        paramOldNames: Array<String>,
        paramNewNames: Array<String>,
        paramNewTypes: Array<String>,
        paramDefaults: Array<String>,
    ): RefactoringRunner.Outcome {
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return RefactoringRunner.Outcome.Failure("type $declaringTypeFqn not found on classpath")
        val method: IMethod = pickMethod(type, oldMethodName, paramTypeSignatures)
            ?: return RefactoringRunner.Outcome.Failure(
                "method $oldMethodName not found on $declaringTypeFqn" +
                    (paramTypeSignatures?.joinToString(",")?.let { " ($it)" } ?: ""),
            )

        val size = paramKinds.size
        require(
            paramOldNames.size == size &&
                paramNewNames.size == size &&
                paramNewTypes.size == size &&
                paramDefaults.size == size,
        ) { "parallel arrays must all be size $size" }

        val processor = ChangeSignatureProcessor(method)
        if (newMethodName.isNotEmpty()) processor.setNewMethodName(newMethodName)
        if (newReturnType.isNotEmpty()) processor.setNewReturnTypeName(newReturnType)

        val originals = processor.parameterInfos.toList()
        val seen = mutableSetOf<String>()
        val rebuilt = mutableListOf<ParameterInfo>()
        for (i in 0 until size) {
            when (val kind = paramKinds[i]) {
                "existing" -> {
                    val oldName = paramOldNames[i]
                    val info = originals.firstOrNull { it.oldName == oldName }
                        ?: return RefactoringRunner.Outcome.Failure(
                            "original parameter $oldName not found on $oldMethodName",
                        )
                    if (!seen.add(oldName)) {
                        return RefactoringRunner.Outcome.Failure("parameter $oldName referenced twice")
                    }
                    if (paramNewNames[i].isNotEmpty()) info.newName = paramNewNames[i]
                    if (paramNewTypes[i].isNotEmpty()) info.newTypeName = paramNewTypes[i]
                    rebuilt += info
                }
                "added" -> {
                    rebuilt += ParameterInfo.createInfoForAddedParameter(
                        paramNewTypes[i],
                        paramNewNames[i],
                        paramDefaults[i],
                    )
                }
                else -> return RefactoringRunner.Outcome.Failure("unknown parameter kind: $kind")
            }
        }
        for (orig in originals) {
            if (orig.oldName !in seen) orig.markAsDeleted()
        }

        processor.parameterInfos.clear()
        processor.parameterInfos.addAll(rebuilt)
        // Append deleted ones so JDT sees them and removes them from
        // the signature / call sites rather than leaving them in place.
        for (orig in originals) {
            if (orig.isDeleted) processor.parameterInfos.add(orig)
        }

        return RefactoringRunner.run(ProcessorBasedRefactoring(processor))
    }

    private fun pickMethod(type: IType, name: String, paramTypeSignatures: Array<String>?): IMethod? {
        if (paramTypeSignatures != null) {
            return type.getMethod(name, paramTypeSignatures).takeIf { it.exists() }
        }
        val candidates = type.methods.filter { it.elementName == name }
        return candidates.singleOrNull()
    }
}
