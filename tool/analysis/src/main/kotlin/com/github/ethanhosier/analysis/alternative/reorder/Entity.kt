package com.github.ethanhosier.analysis.alternative.reorder

/**
 * Unit of dependency tracking between mined refactoring specs.
 *
 * Two specs that touch the same [Entity] value (read/write/produce/
 * consume) imply an ordering constraint in the
 * [SpecDependencyAnalyzer] DAG.
 *
 * Identity is by structural equality of the data classes — the
 * analyser doesn't carry node-AST references, only the symbolic
 * handles a [com.github.ethanhosier.analysis.miner.model.RefactoringSpec]
 * can name from its own fields.
 */
sealed interface Entity {

    /**
     * Class / interface, identified by FQN.
     *
     * [version] is assigned by [SpecVersioner] in the analyser's
     * pre-pass: 0 = pre-window state (the entity existed before any
     * spec ran), and otherwise = trace index of the spec that
     * produced this version. Two productions of the same FQN within
     * a window therefore become two distinct entities.
     */
    data class Type(val fqn: String, val version: Int = 0) : Entity

    /** Method, identified by declaring type FQN + name + erased param types. */
    data class Method(
        val declaringTypeFqn: String,
        val name: String,
        val paramTypeSignatures: ParamTypes,
        val version: Int = 0,
    ) : Entity

    /** Instance / static field on a class. */
    data class Field(val declaringTypeFqn: String, val name: String, val version: Int = 0) : Entity

    /** Package namespace. */
    data class Package(val name: String, val version: Int = 0) : Entity

    /**
     * Mutable interior of a host method body. Two specs that mutate
     * the same body must be ordered (their AST-subtree-hash anchors
     * are computed against a specific body state).
     */
    data class HostMethodBody(
        val declaringTypeFqn: String,
        val methodName: String,
        val paramTypeSignatures: List<String>,
    ) : Entity

    /**
     * A specific local variable / parameter declaration, anchored by
     * its AST subtree hash inside a host method body. Two specs whose
     * anchor hashes match must be talking about the same node.
     */
    data class Declaration(
        val host: HostMethodBody,
        val declarationSubtreeHash: String,
    ) : Entity

    /**
     * A specific code region in a host body, anchored by selection
     * subtree hash. Distinct from [Declaration] because a region may
     * span multiple statements.
     */
    data class Region(
        val host: HostMethodBody,
        val selectionSubtreeHash: String,
    ) : Entity
}

/**
 * Param-type list as carried on [Entity.Method]. Either a known list
 * (which compares by structural equality) or [Opaque] — a fresh
 * sentinel that compares equal only to itself across instances.
 *
 * Used when a spec produces a [Entity.Method] whose param types we
 * can't recover from the spec fields alone (e.g. ExtractMethod's new
 * method's signature depends on data-flow analysis we don't run
 * here).
 *
 * The dependency analyser has a carve-out: when one side is [Opaque]
 * and the other is a [Known] list, two methods match if their
 * (declaringTypeFqn, name) agree. This lets a later RenameMethod find
 * a previously-extracted method by name without us needing to guess
 * the param types.
 */
sealed interface ParamTypes {
    data class Known(val list: List<String>) : ParamTypes

    /**
     * Sentinel for "we can't pin down the param list from spec fields
     * alone". Distinct instances are not equal — using `data object`
     * would collapse all opaques into one, defeating the structural
     * mismatch we want for safety.
     */
    class Opaque : ParamTypes {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
        override fun toString(): String = "Opaque#${System.identityHashCode(this)}"
    }
}
