package org.modelix.metamodel

import org.modelix.model.api.INode

interface ITypedNode {
    val _concept: ITypedConcept
    val _node: INode
}