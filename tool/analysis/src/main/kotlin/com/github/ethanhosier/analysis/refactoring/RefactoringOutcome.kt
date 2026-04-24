package com.github.ethanhosier.analysis.refactoring

import java.nio.file.Path

sealed interface RefactoringOutcome {
    data class Success(val changedFiles: List<Path>) : RefactoringOutcome
    data class Failed(val reason: String) : RefactoringOutcome
}
