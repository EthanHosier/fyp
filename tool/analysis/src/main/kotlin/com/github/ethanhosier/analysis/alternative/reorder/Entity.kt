package com.github.ethanhosier.analysis.alternative.reorder

sealed interface Entity {

    data class Type(val fqn: String, val version: Int = 0) : Entity

    data class Method(
        val declaringTypeFqn: String,
        val name: String,
        val paramTypeSignatures: ParamTypes,
        val version: Int = 0,
    ) : Entity

    data class Field(val declaringTypeFqn: String, val name: String, val version: Int = 0) : Entity

    data class Package(val name: String, val version: Int = 0) : Entity

    data class HostMethodBody(
        val declaringTypeFqn: String,
        val methodName: String,
        val paramTypeSignatures: List<String>,
    ) : Entity

    data class Declaration(
        val host: HostMethodBody,
        val declarationSubtreeHash: String,
    ) : Entity

    data class Region(
        val host: HostMethodBody,
        val selectionSubtreeHash: String,
    ) : Entity
}

sealed interface ParamTypes {
    data class Known(val list: List<String>) : ParamTypes

    class Opaque : ParamTypes {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
        override fun toString(): String = "Opaque#${System.identityHashCode(this)}"
    }
}
