package com.github.ethanhosier.refactoringbundle

import com.github.ethanhosier.refactoringbundle.internal.RefactoringHost
import com.github.ethanhosier.refactoringbundle.internal.ops.ChangeAttributeTypeOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ChangeMethodSignatureOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ChangeVariableTypeOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractAttributeOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractClassOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractInterfaceOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractSubclassOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ParameterizeVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ReplaceVariableWithAttributeOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractSuperclassOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.InlineMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.InlineVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.MoveClassOp
import com.github.ethanhosier.refactoringbundle.internal.ops.MoveInstanceFieldOp
import com.github.ethanhosier.refactoringbundle.internal.ops.MoveInstanceMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.MoveStaticMembersOp
import com.github.ethanhosier.refactoringbundle.internal.ops.PullUpOp
import com.github.ethanhosier.refactoringbundle.internal.ops.PushDownOp
import com.github.ethanhosier.refactoringbundle.internal.ops.RenameClassOp
import com.github.ethanhosier.refactoringbundle.internal.ops.RenameFieldOp
import com.github.ethanhosier.refactoringbundle.internal.ops.RenameLocalVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.RenameMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.RenamePackageOp
import org.eclipse.core.runtime.preferences.DefaultScope
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jdt.core.manipulation.JavaManipulation
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType
import org.eclipse.jface.text.templates.Template
import org.eclipse.text.templates.ContextTypeRegistry
import org.eclipse.text.templates.TemplatePersistenceData
import org.eclipse.text.templates.TemplateStoreCore

/**
 * The bundle-side entry point for JDT-backed refactorings. Every public
 * method has a primitive-only signature (String / Int / Array<String>)
 * and returns a JSON-encoded outcome string — both so the host can
 * invoke them reflectively across the OSGi classloader boundary
 * without needing to load any Eclipse types itself.
 *
 * Each `@JvmStatic` method here is a thin delegation to a
 * [com.github.ethanhosier.refactoringbundle.internal.ops] object wrapped
 * in [RefactoringHost.run]. Adding a new refactoring = one new
 * `ops/<Name>Op.kt` + one delegation line here.
 */
object JdtRefactorer {

    init {
        // Normally set by the jdt.ui plugin activator; refactorings
        // look up formatting / import-organisation prefs under this
        // node. Without it `ProjectScope.getNode(null)` throws IAE from
        // inside the refactoring condition checks.
        val nodeId = "org.eclipse.jdt.core"
        JavaManipulation.setPreferenceNodeId(nodeId)

        val node = DefaultScope.INSTANCE.getNode(nodeId)
        node.put("org.eclipse.jdt.ui.importorder", "java;javax;org;com")
        node.put("org.eclipse.jdt.ui.ondemandthreshold", "99")
        node.put("org.eclipse.jdt.ui.staticondemandthreshold", "99")

        // Force spaces for new code inserted by refactorings so output
        // doesn't mix spaces (existing code) with tabs (JDT default).
        node.put("org.eclipse.jdt.core.formatter.tabulation.char", "space")
        node.put("org.eclipse.jdt.core.formatter.tabulation.size", "4")
        node.put("org.eclipse.jdt.core.formatter.indentation.size", "4")

        // Normally invoked by the jdt.ui plugin activator. Without it
        // member-structure refactorings (Pull Up / Push Down / Move
        // Static Members) NPE the moment they try to read the members
        // ordering prefs cache.
        JavaManipulationPlugin.getDefault().membersOrderPreferenceCacheCommon.install()

        // Extract Interface / Superclass / Class look up code templates
        // for generated method stubs + comments. Without a store, they
        // NPE in TemplateStoreCore. An empty store is fine — JDT falls
        // back to built-in defaults per template id.
        if (JavaManipulation.getCodeTemplateContextRegistry() == null) {
            val registry = ContextTypeRegistry()
            registry.addContextType(CodeTemplateContextType(CodeTemplateContextType.NEWTYPE_CONTEXTTYPE))
            registry.addContextType(CodeTemplateContextType(CodeTemplateContextType.FILECOMMENT_CONTEXTTYPE))
            registry.addContextType(CodeTemplateContextType(CodeTemplateContextType.TYPECOMMENT_CONTEXTTYPE))
            JavaManipulation.setCodeTemplateContextRegistry(registry)
        }

        if (JavaManipulation.getCodeTemplateStore() == null) {
            val store = TemplateStoreCore(InstanceScope.INSTANCE.getNode(nodeId), "$nodeId.codetemplates")
            // Extract Class (via ParameterObjectFactory) generates the
            // new CU by calling CodeGeneration.getCompilationUnitContent,
            // which returns null if the NEWTYPE template is missing —
            // the null then NPEs inside setContents. jdt.ui normally
            // seeds this from its plugin.xml CodeTemplates resource;
            // we seed the minimal default here.
            store.add(
                TemplatePersistenceData(
                    Template(
                        "newtype",
                        "Newly created files",
                        CodeTemplateContextType.NEWTYPE_CONTEXTTYPE,
                        "\${filecomment}\n\${package_declaration}\n\n\${typecomment}\n\${type_declaration}",
                        true,
                    ),
                    true,
                    CodeTemplateContextType.NEWTYPE_ID,
                ),
            )
            JavaManipulation.setCodeTemplateStore(store)
        }
    }

    @JvmStatic
    fun extractMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        startLine: Int,
        endLine: Int,
        newMethodName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractMethodOp.run(jp, relativeFilePath, startLine, endLine, newMethodName)
    }

    @JvmStatic
    fun renameMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
        paramTypeSignatures: Array<String>?,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        RenameMethodOp.run(jp, declaringTypeFqn, oldName, newName, paramTypeSignatures)
    }

    @JvmStatic
    fun renameClass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        typeFqn: String,
        newName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        RenameClassOp.run(jp, typeFqn, newName)
    }

    @JvmStatic
    fun renameField(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        RenameFieldOp.run(jp, declaringTypeFqn, oldName, newName)
    }

    @JvmStatic
    fun renamePackage(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        oldPackage: String,
        newPackage: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        RenamePackageOp.run(jp, oldPackage, newPackage)
    }

    @JvmStatic
    fun renameLocalVariable(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        RenameLocalVariableOp.run(jp, relativeFilePath, line, column, newName)
    }

    @JvmStatic
    fun extractVariable(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractVariableOp.run(jp, relativeFilePath, startLine, startColumn, endLine, endColumn, newName)
    }

    @JvmStatic
    fun inlineVariable(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        line: Int,
        column: Int,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        InlineVariableOp.run(jp, relativeFilePath, line, column)
    }

    @JvmStatic
    fun inlineMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        methodName: String,
        paramTypeSignatures: Array<String>?,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        InlineMethodOp.run(jp, declaringTypeFqn, methodName, paramTypeSignatures)
    }

    @JvmStatic
    fun pullUp(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        PullUpOp.run(jp, declaringTypeFqn, methodNames, fieldNames)
    }

    @JvmStatic
    fun pushDown(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        PushDownOp.run(jp, declaringTypeFqn, methodNames, fieldNames)
    }

    @JvmStatic
    fun moveStaticMembers(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        destinationTypeFqn: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        MoveStaticMembersOp.run(jp, sourceTypeFqn, destinationTypeFqn, methodNames, fieldNames)
    }

    @JvmStatic
    fun moveInstanceMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        methodName: String,
        targetName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        MoveInstanceMethodOp.run(jp, sourceTypeFqn, methodName, targetName)
    }

    @JvmStatic
    fun moveInstanceField(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        fieldName: String,
        destinationTypeFqn: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        MoveInstanceFieldOp.run(jp, sourceTypeFqn, fieldName, destinationTypeFqn)
    }

    @JvmStatic
    fun moveClass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        typeFqn: String,
        destinationPackage: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        MoveClassOp.run(jp, typeFqn, destinationPackage)
    }

    @JvmStatic
    fun extractInterface(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        newInterfaceName: String,
        methodNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractInterfaceOp.run(jp, sourceTypeFqn, newInterfaceName, methodNames)
    }

    @JvmStatic
    fun changeVariableType(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newTypeFqn: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ChangeVariableTypeOp.run(jp, relativeFilePath, line, column, newTypeFqn)
    }

    @JvmStatic
    fun changeAttributeType(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        fieldName: String,
        newTypeFqn: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ChangeAttributeTypeOp.run(jp, declaringTypeFqn, fieldName, newTypeFqn)
    }

    @JvmStatic
    fun changeMethodSignature(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
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
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ChangeMethodSignatureOp.run(
            jp,
            declaringTypeFqn,
            oldMethodName,
            paramTypeSignatures,
            newMethodName,
            newReturnType,
            paramKinds,
            paramOldNames,
            paramNewNames,
            paramNewTypes,
            paramDefaults,
        )
    }

    @JvmStatic
    fun extractAttribute(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newName: String,
        visibility: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractAttributeOp.run(jp, relativeFilePath, startLine, startColumn, endLine, endColumn, newName, visibility)
    }

    @JvmStatic
    fun extractClass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        newClassName: String,
        delegateFieldName: String,
        fieldNames: Array<String>,
        createGetterSetter: Boolean,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractClassOp.run(jp, sourceTypeFqn, newClassName, delegateFieldName, fieldNames, createGetterSetter)
    }

    @JvmStatic
    fun extractSubclass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        newSubclassName: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractSubclassOp.run(jp, sourceTypeFqn, newSubclassName, methodNames, fieldNames)
    }

    @JvmStatic
    fun parameterizeVariable(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        newParameterName: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ParameterizeVariableOp.run(jp, relativeFilePath, startLine, startColumn, endLine, endColumn, newParameterName)
    }

    @JvmStatic
    fun replaceVariableWithAttribute(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        line: Int,
        column: Int,
        newFieldName: String,
        visibility: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ReplaceVariableWithAttributeOp.run(jp, relativeFilePath, line, column, newFieldName, visibility)
    }

    @JvmStatic
    fun extractSuperclass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        sourceTypeFqn: String,
        newSupertypeName: String,
        methodNames: Array<String>,
        fieldNames: Array<String>,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        ExtractSuperclassOp.run(jp, sourceTypeFqn, newSupertypeName, methodNames, fieldNames)
    }
}
