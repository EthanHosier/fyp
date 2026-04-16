package com.github.ethanhosier.analysis.miner

/**
 * Allowlist of RefactoringMiner detection types that correspond to a
 * first-class built-in IntelliJ refactoring action.
 *
 * Every `DetectedRefactoring.ideRelevant` is decided by membership in this
 * set. Matched against `RefactoringType.displayName` (e.g.
 * `"Extract Method"`) — stable strings in RM's public API.
 *
 * The set errs on the side of inclusion: anything a user could have
 * invoked via Refactor → … in IntelliJ is marked relevant. Types like
 * `Add Method Annotation` that are plain edits (no IDE refactoring action)
 * are left out.
 */
object IdeRelevantRefactorings {

    private val names: Set<String> = setOf(
        // Extract / Inline
        "Extract Method",
        "Inline Method",
        "Extract Variable",
        "Inline Variable",
        "Extract Attribute",
        "Extract Class",
        "Extract Subclass",
        "Extract Superclass",
        "Extract Interface",

        // Rename
        "Rename Class",
        "Rename Method",
        "Rename Attribute",
        "Rename Variable",
        "Rename Parameter",
        "Rename Package",

        // Move
        "Move Class",
        "Move Method",
        "Move Attribute",
        "Move Package",
        "Move And Rename Class",
        "Move And Rename Method",
        "Move And Rename Attribute",
        "Move Source Folder",

        // Hierarchy
        "Pull Up Method",
        "Pull Up Attribute",
        "Push Down Method",
        "Push Down Attribute",
        "Extract And Move Method",

        // Change Signature (IntelliJ's single action covers all of these)
        "Add Parameter",
        "Remove Parameter",
        "Reorder Parameter",
        "Change Parameter Type",
        "Change Return Type",
        "Change Variable Type",
        "Change Attribute Type",
        "Parameterize Variable",
        "Parameterize Attribute",
        "Replace Variable With Attribute",
    )

    fun isIdeRelevant(type: String): Boolean = type in names
}
