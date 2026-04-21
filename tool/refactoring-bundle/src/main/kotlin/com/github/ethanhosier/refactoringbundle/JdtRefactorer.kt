package com.github.ethanhosier.refactoringbundle

import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.preferences.DefaultScope
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.Modifier
import org.eclipse.jdt.core.manipulation.JavaManipulation
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring
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
        // from inside `ExtractMethodRefactoring.checkInitialConditions`.
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

        val fileResource = project.getFile(relativeFilePath)
        val icu = JavaCore.createCompilationUnitFrom(fileResource)
            ?: error("JDT couldn't open compilation unit at $relativeFilePath")

        val selStart = lineOffset(source, startLine)
        val selEnd = lineOffset(source, endLine + 1)
        val selLength = selEnd - selStart

        val refactoring = ExtractMethodRefactoring(icu, selStart, selLength).apply {
            methodName = newMethodName
            visibility = Modifier.PRIVATE
        }

        val pm = NullProgressMonitor()
        val initial = refactoring.checkInitialConditions(pm)
        check(!initial.hasFatalError()) { "Initial conditions failed: $initial" }
        val final = refactoring.checkFinalConditions(pm)
        check(!final.hasFatalError()) { "Final conditions failed: $final" }
        val change = refactoring.createChange(pm)
        change.perform(pm)

        return Files.readString(onDisk)
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
