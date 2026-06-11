package com.github.ethanhosier.analysis.miner

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
