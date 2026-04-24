package com.github.ethanhosier.refactoringbundle

import com.github.ethanhosier.refactoringbundle.internal.IndexingGate
import com.github.ethanhosier.refactoringbundle.internal.OutcomeJson
import com.github.ethanhosier.refactoringbundle.internal.ProjectInitializer
import com.github.ethanhosier.refactoringbundle.internal.RefactoringRunner
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.preferences.DefaultScope
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.core.manipulation.JavaManipulation
import org.eclipse.jdt.core.refactoring.IJavaRefactorings
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring
import org.eclipse.ltk.core.refactoring.RefactoringCore
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import java.nio.file.Path
import java.util.UUID

/**
 * The bundle-side entry point for JDT-backed refactorings. Every public
 * method has a primitive-only signature (String / Int / Array<String>)
 * and returns a JSON-encoded outcome string — both so the host can
 * invoke them reflectively across the OSGi classloader boundary
 * without needing to load any Eclipse types itself.
 *
 * Each call creates a fresh Eclipse project pointing at the caller's
 * worktree, runs the refactoring, then deletes the project metadata
 * (the worktree files stay). No cross-call state.
 *
 * Adding a new refactoring: add a `@JvmStatic` method here that
 * resolves the target element via [withProject], constructs the
 * appropriate JDT descriptor, and delegates to [RefactoringRunner.run].
 * Should be ~15–25 LoC.
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
    }

    /**
     * Extract lines `[startLine, endLine]` of the file at
     * [relativeFilePath] into a new private method [newMethodName].
     * Lines are 1-indexed and inclusive.
     */
    @JvmStatic
    fun extractMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        relativeFilePath: String,
        startLine: Int,
        endLine: Int,
        newMethodName: String,
    ): String = withProject(projectRoot, sourceFolders, classpathJars) { javaProject ->
        val icu = findCompilationUnit(javaProject, relativeFilePath)
            ?: return@withProject RefactoringRunner.Outcome.Failure(
                "no compilation unit at $relativeFilePath",
            )

        val source = String(icu.buffer.characters)
        val selStart = lineOffset(source, startLine)
        val selEnd = lineOffset(source, endLine + 1)
        val selLength = selEnd - selStart

        val refactoring = ExtractMethodRefactoring(icu, selStart, selLength).apply {
            methodName = newMethodName
            visibility = Modifier.PRIVATE
        }
        RefactoringRunner.run(refactoring)
    }

    /**
     * Rename a method declared on [declaringTypeFqn] from [oldName] to
     * [newName] across the entire project. Optional
     * [paramTypeSignatures] disambiguates overloads (JDT-encoded:
     * `Ljava/lang/String;`, `I`, `V`, …).
     */
    @JvmStatic
    fun renameMethod(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
        paramTypeSignatures: Array<String>?,
    ): String = withProject(projectRoot, sourceFolders, classpathJars) { javaProject ->
        val type: IType = javaProject.findType(declaringTypeFqn)
            ?: return@withProject RefactoringRunner.Outcome.Failure(
                "type $declaringTypeFqn not found on classpath",
            )
        val method: IMethod = pickMethod(type, oldName, paramTypeSignatures)
            ?: return@withProject RefactoringRunner.Outcome.Failure(
                "method $oldName not found on $declaringTypeFqn" +
                    (paramTypeSignatures?.joinToString(",")?.let { " ($it)" } ?: ""),
            )

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_METHOD)
            ?: return@withProject RefactoringRunner.Outcome.Failure(
                "Rename Method refactoring contribution not available",
            )
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(javaProject.project.name)
        descriptor.setJavaElement(method)
        descriptor.setNewName(newName)
        descriptor.setUpdateReferences(true)

        val status = RefactoringStatus()
        val refactoring = descriptor.createRefactoring(status)
            ?: return@withProject RefactoringRunner.Outcome.Failure(
                "failed to construct rename refactoring: ${status.entries.joinToString("; ") { it.message }}",
            )
        RefactoringRunner.run(refactoring)
    }

    // -- helpers --------------------------------------------------------

    // Wraps one refactoring: init a fresh Eclipse project against the
    // caller's worktree, wait for indexing, hand it to [body], then
    // delete the project metadata regardless of outcome. Exceptions
    // and typed failures are both serialised to JSON for the host.
    private inline fun withProject(
        projectRoot: String,
        sourceFolders: Array<String>,
        classpathJars: Array<String>,
        body: (IJavaProject) -> RefactoringRunner.Outcome,
    ): String {
        val name = "rc-" + UUID.randomUUID().toString().replace("-", "")
        val javaProject: IJavaProject = try {
            val jp = ProjectInitializer.initProject(
                name = name,
                root = Path.of(projectRoot),
                sourceFolders = sourceFolders.toList(),
                classpathJars = classpathJars.map(Path::of),
            )
            IndexingGate.waitForIndex(jp)
            jp
        } catch (t: Throwable) {
            return OutcomeJson.failed("project init failed: ${t::class.simpleName}: ${t.message}")
        }

        return try {
            when (val outcome = body(javaProject)) {
                is RefactoringRunner.Outcome.Success -> OutcomeJson.ok(outcome.changedFiles)
                is RefactoringRunner.Outcome.Failure -> OutcomeJson.failed(outcome.reason)
            }
        } catch (t: Throwable) {
            val cause = generateSequence<Throwable>(t) { it.cause }.last()
            OutcomeJson.failed("${t::class.simpleName}: ${t.message ?: "<no message>"} | root=${cause::class.simpleName}: ${cause.message}")
        } finally {
            // deleteContent=false — preserves the caller's worktree
            // files; only removes Eclipse's `.project` metadata.
            runCatching {
                javaProject.project.delete(false, true, NullProgressMonitor())
            }
        }
    }

    private fun findCompilationUnit(
        javaProject: IJavaProject,
        relativeFilePath: String,
    ): ICompilationUnit? {
        val file: IFile = javaProject.project.getFile(relativeFilePath).takeIf { it.exists() }
            ?: return null
        return JavaCore.createCompilationUnitFrom(file)
    }

    private fun pickMethod(
        type: IType,
        name: String,
        paramTypeSignatures: Array<String>?,
    ): IMethod? {
        if (paramTypeSignatures != null) {
            return type.getMethod(name, paramTypeSignatures).takeIf { it.exists() }
        }
        // Unambiguous by name? Return it. Otherwise refuse so the
        // caller has to disambiguate explicitly rather than picking a
        // surprising overload for them.
        val candidates = type.methods.filter { it.elementName == name }
        return candidates.singleOrNull()
    }

    private fun lineOffset(source: String, line: Int): Int {
        if (line <= 1) return 0
        var idx = 0
        var seen = 1
        while (seen < line && idx < source.length) {
            if (source[idx] == '\n') seen++
            idx++
        }
        return idx
    }
}
