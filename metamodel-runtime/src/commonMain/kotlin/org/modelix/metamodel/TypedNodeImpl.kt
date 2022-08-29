package org.modelix.metamodel

import org.modelix.model.api.INode

abstract class TypedNodeImpl(override val _node: INode) : ITypedNode {
    abstract val concept: ITypedConcept

    init {
        require(_node.concept == concept) { "Concept of node $_node expected to be $concept, but was ${_node.concept}" }
        (concept.concept.language as? GeneratedLanguage)?.assertRegistered()
    }
}

