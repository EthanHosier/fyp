package com.github.ethanhosier.ideplugin.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    // Editor / file interaction
    EDIT_BURST,
    FILE_OPENED,
    FILE_CLOSED,
    FILE_FOCUSED,
    FILE_UNFOCUSED,
    FILE_SAVED,

    // File structure
    FILE_CREATED,
    FILE_DELETED,
    FILE_RENAMED,
    FILE_MOVED,
    FILE_MODIFIED_EXTERNAL,

    // IntelliJ refactoring
    REFACTORING_STARTED,
    REFACTORING_FINISHED,

    // Build / test
    BUILD_STARTED,
    BUILD_FINISHED,
    TEST_RUN_STARTED,
    TEST_RUN_FINISHED,

    // Session lifecycle
    SESSION_STARTED,
    SESSION_ENDED,

    // VCS
    GIT_COMMIT,
}
