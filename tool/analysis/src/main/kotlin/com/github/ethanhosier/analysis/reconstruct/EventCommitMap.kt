package com.github.ethanhosier.analysis.reconstruct

import kotlinx.serialization.Serializable

/**
 * Mapping from event id to the commit SHA produced for that event in the
 * shadow repo.
 *
 * Events with no state-bearing effect (no changedFiles, or only no-op edits)
 * map to the SHA of the most recent preceding commit — so every event
 * resolves to a commit, and "what did the project look like at event E?"
 * is always a single checkout.
 *
 * Iteration order follows the normalized event order.
 */
@Serializable
data class EventCommitMap(val mapping: Map<String, String>)
