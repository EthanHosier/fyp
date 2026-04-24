package com.github.ethanhosier.refactoringbundle

import com.github.ethanhosier.refactoringbundle.internal.RefactoringHost
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.ExtractVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.InlineMethodOp
import com.github.ethanhosier.refactoringbundle.internal.ops.InlineVariableOp
import com.github.ethanhosier.refactoringbundle.internal.ops.MoveClassOp
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
import org.eclipse.jdt.core.manipulation.JavaManipulation
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin

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
    fun moveClass(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        typeFqn: String,
        destinationPackage: String,
    ): String = RefactoringHost.run(projectRoot, sourceFolders, classpathJars) { jp ->
        MoveClassOp.run(jp, typeFqn, destinationPackage)
    }
}
