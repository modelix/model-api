package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.IProperty

interface ITypedNode {
    val _concept: ITypedConcept
    val _node: INode
}

fun ITypedNode.getPropertyValue(property: IProperty): String? = _node.getPropertyValue(property.name)