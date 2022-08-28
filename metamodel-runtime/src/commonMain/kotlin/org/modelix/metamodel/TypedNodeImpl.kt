package org.modelix.metamodel

import org.modelix.model.api.INode

abstract class TypedNodeImpl(override val node: INode) : ITypedNode {
    abstract val concept: ITypedConcept

    init {
        require(node.concept == concept) { "Concept of node $node expected to be $concept, but was ${node.concept}" }
        (concept.concept.language as? GeneratedLanguage)?.assertRegistered()
    }
}

