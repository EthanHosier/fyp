package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path as EclipsePath
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.JavaRuntime
import java.nio.file.Path

/**
 * Turns a plain filesystem directory (typically a git worktree) into an
 * Eclipse `IJavaProject` the refactoring engine can operate on. The
 * project's physical `location` is the worktree itself — no copying —
 * so refactorings land on the caller's files directly.
 */
internal object ProjectInitializer {

    fun initProject(
        name: String,
        root: Path,
        sourceFolders: List<String>,
        classpathJars: List<Path>,
    ): IJavaProject {
        val workspace = ResourcesPlugin.getWorkspace()
        val project = workspace.root.getProject(name)

        if (!project.exists()) {
            val desc: IProjectDescription = workspace.newProjectDescription(name)
            desc.natureIds = arrayOf(JavaCore.NATURE_ID)
            // Physical project location = the worktree. Edits made via
            // JDT land on disk at `root/…`, which is exactly what the
            // caller wants.
            desc.setLocation(EclipsePath(root.toAbsolutePath().toString()))
            project.create(desc, NullProgressMonitor())
        }
        project.open(NullProgressMonitor())
        project.refreshLocal(IResource.DEPTH_INFINITE, NullProgressMonitor())

        val javaProject = JavaCore.create(project)

        val entries = mutableListOf<IClasspathEntry>()
        for (folder in sourceFolders) {
            val srcResource = project.getFolder(folder)
            if (srcResource.exists()) {
                entries += JavaCore.newSourceEntry(srcResource.fullPath)
            }
        }
        // JRE container — without this the type bindings for `String`,
        // `Object`, etc. are unresolved, and several refactorings bail
        // during `checkFinalConditions` on "unknown type" errors.
        entries += JavaRuntime.getDefaultJREContainerEntry()
        for (jar in classpathJars) {
            entries += JavaCore.newLibraryEntry(
                EclipsePath(jar.toAbsolutePath().toString()),
                null, null,
            )
        }

        val binFolder = project.getFolder(".jdt-bin")
        if (!binFolder.exists()) binFolder.create(true, true, NullProgressMonitor())

        javaProject.setRawClasspath(
            entries.toTypedArray(),
            binFolder.fullPath,
            NullProgressMonitor(),
        )

        return javaProject
    }
}
