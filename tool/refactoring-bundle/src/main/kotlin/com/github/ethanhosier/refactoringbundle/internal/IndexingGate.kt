package com.github.ethanhosier.refactoringbundle.internal

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.TypeNameRequestor

/**
 * Blocking wait for JDT's background indexer to be idle. After
 * `setRawClasspath` the indexer runs asynchronously; refactorings that
 * search for references need it settled, otherwise they either miss
 * call sites or throw from `checkFinalConditions` mid-index.
 *
 * `WAIT_UNTIL_READY_TO_SEARCH` is the JDT-sanctioned way to do this:
 * the engine will not return from the search until the index covers
 * the project's scope.
 */
internal object IndexingGate {

    fun waitForIndex(javaProject: IJavaProject) {
        val scope = SearchEngine.createJavaSearchScope(arrayOf(javaProject))
        val requestor = object : TypeNameRequestor() {}
        SearchEngine().searchAllTypeNames(
            null,
            SearchPattern.R_EXACT_MATCH,
            "__forceIndex__".toCharArray(),
            SearchPattern.R_EXACT_MATCH,
            IJavaSearchConstants.TYPE,
            scope,
            requestor,
            IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
            NullProgressMonitor(),
        )
    }
}
