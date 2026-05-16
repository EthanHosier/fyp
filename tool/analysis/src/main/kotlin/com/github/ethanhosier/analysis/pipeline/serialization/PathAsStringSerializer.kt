package com.github.ethanhosier.analysis.pipeline.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Serialises a [Path] as its string form. Used on Phase-A artefact fields
 * (e.g. `ReconstructionResult.repoDir`) that need to round-trip through
 * JSON for the `:phaseB` cached-replay flow but where the absolute path
 * is the only thing downstream consumers care about.
 */
object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.nio.file.Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Path =
        Paths.get(decoder.decodeString())
}
