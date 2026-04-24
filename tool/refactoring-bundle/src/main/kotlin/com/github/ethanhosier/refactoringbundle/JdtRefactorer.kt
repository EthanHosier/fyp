package com.github.ethanhosier.refactoringbundle

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.preferences.DefaultScope
import org.eclipse.jdt.core.ICompilationUnit
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Entry point invoked from the embedding host after Equinox boots.
 * Lives inside an OSGi bundle so the classloader used to load this
 * class is the same one that loads `IWorkspace` / `ICompilationUnit` /
 * `ExtractMethodRefactoring` from their bundles — no classloader split
 * when the host reflectively calls us.
 *
 * Each call creates a disposable Eclipse project inside the workspace,
 * drops the source file in it, runs the refactoring, and returns the
 * rewritten source. The project is named uniquely per call so repeat
 * invocations don't collide.
 */
object JdtRefactorer {

    init {
        // Normally set by the jdt.ui plugin activator; refactorings
        // look up formatting / import-organisation prefs under this
        // node. Without it `ProjectScope.getNode(null)` throws IAE
        // from inside the refactoring condition checks.
        val nodeId = "org.eclipse.jdt.core"
        JavaManipulation.setPreferenceNodeId(nodeId)

        // Seed the import-rewrite prefs jdt.ui would normally provide.
        // Without these, the import organiser blows up on null strings.
        val node = DefaultScope.INSTANCE.getNode(nodeId)
        node.put("org.eclipse.jdt.ui.importorder", "java;javax;org;com")
        node.put("org.eclipse.jdt.ui.ondemandthreshold", "99")
        node.put("org.eclipse.jdt.ui.staticondemandthreshold", "99")
    }

    /**
     * Extract lines `[startLine, endLine]` of [source] into a new
     * method `newMethodName` and return the rewritten source. Lines
     * are 1-indexed and inclusive.
     */
    @JvmStatic
    fun extractMethod(
        projectName: String,
        relativeFilePath: String,
        source: String,
        startLine: Int,
        endLine: Int,
        newMethodName: String,
    ): String {
        val (onDisk, icu) = materialise(projectName, relativeFilePath, source)

        val selStart = lineOffset(source, startLine)
        val selEnd = lineOffset(source, endLine + 1)
        val selLength = selEnd - selStart

        val refactoring = ExtractMethodRefactoring(icu, selStart, selLength).apply {
            methodName = newMethodName
            visibility = Modifier.PRIVATE
        }
        runRefactoring(refactoring)

        return Files.readString(onDisk)
    }

    /**
     * Rename a method declared on [declaringTypeFqn] from [oldName] to
     * [newName] and return the rewritten source. Updates every call
     * site within the file. Picks the first method with a matching
     * name — overloaded methods aren't disambiguated yet.
     */
    @JvmStatic
    fun renameMethod(
        projectName: String,
        relativeFilePath: String,
        source: String,
        declaringTypeFqn: String,
        oldName: String,
        newName: String,
    ): String {
        val (onDisk, icu) = materialise(projectName, relativeFilePath, source)

        // Look up the type inside the ICU directly — avoids needing a
        // fully-configured classpath for JDT's type index. Handles the
        // top-level type only (no nested classes) which is enough for
        // single-file refactorings.
        val simpleName = declaringTypeFqn.substringAfterLast('.')
        val type: IType = icu.types.firstOrNull { it.elementName == simpleName }
            ?: error("CU $relativeFilePath has no top-level type named $simpleName")
        val method: IMethod = type.methods.firstOrNull { it.elementName == oldName }
            ?: error("Type $declaringTypeFqn has no method named $oldName")

        val contribution = RefactoringCore.getRefactoringContribution(IJavaRefactorings.RENAME_METHOD)
            ?: error("Rename Method refactoring contribution not available")
        val descriptor = contribution.createDescriptor() as RenameJavaElementDescriptor
        descriptor.setProject(projectName)
        descriptor.setJavaElement(method)
        descriptor.setNewName(newName)
        descriptor.setUpdateReferences(true)

        val status = RefactoringStatus()
        val refactoring = descriptor.createRefactoring(status)
            ?: error("Failed to construct rename refactoring: $status")
        runRefactoring(refactoring)

        return Files.readString(onDisk)
    }

    // Ensures the project exists, writes `source` to the relative path
    // inside it, and returns the on-disk path + the loaded
    // ICompilationUnit — the shared preamble for every refactoring.
    private fun materialise(
        projectName: String,
        relativeFilePath: String,
        source: String,
    ): Pair<Path, ICompilationUnit> {
        val workspace = ResourcesPlugin.getWorkspace()
        val root = workspace.root
        val project = root.getProject(projectName)
        if (!project.exists()) {
            val desc: IProjectDescription = workspace.newProjectDescription(projectName)
            desc.natureIds = arrayOf(JavaCore.NATURE_ID)
            project.create(desc, NullProgressMonitor())
        }
        project.open(NullProgressMonitor())

        val projectDir = Path.of(project.location.toOSString())
        val onDisk = projectDir.resolve(relativeFilePath)
        onDisk.parent.createDirectories()
        Files.writeString(onDisk, source)
        project.refreshLocal(IResource.DEPTH_INFINITE, NullProgressMonitor())

        val fileResource: IFile = project.getFile(relativeFilePath)
        val icu = JavaCore.createCompilationUnitFrom(fileResource)
            ?: error("JDT couldn't open compilation unit at $relativeFilePath")
        return onDisk to icu
    }

    private fun runRefactoring(refactoring: org.eclipse.ltk.core.refactoring.Refactoring) {
        val pm = NullProgressMonitor()
        val initial = refactoring.checkInitialConditions(pm)
        check(!initial.hasFatalError()) { "Initial conditions failed: $initial" }
        val final = refactoring.checkFinalConditions(pm)
        check(!final.hasFatalError()) { "Final conditions failed: $final" }
        val change = refactoring.createChange(pm)
        change.perform(pm)
    }

    // Offset of the start of the 1-indexed line in `source`. Line
    // `lines+1` resolves to source.length so callers can express
    // "end of line N" as lineOffset(N+1).
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
