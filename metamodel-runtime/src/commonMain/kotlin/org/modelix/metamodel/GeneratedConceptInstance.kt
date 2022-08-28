package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode

abstract class GeneratedConceptInstance(val node: INode) {
    abstract val concept: IConceptWrapper

    init {
        require(node.concept == concept) { "Concept of node $node expected to be $concept, but was ${node.concept}" }
        (concept.concept.language as? GeneratedLanguage)?.assertRegistered()
    }
}

