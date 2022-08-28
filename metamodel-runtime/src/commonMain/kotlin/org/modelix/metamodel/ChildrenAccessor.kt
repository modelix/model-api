package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass
import kotlin.reflect.cast

class ChildrenAccessor<ChildT : ITypedNode>(
    val parent: INode,
    val role: String,
    val childConcept: IConcept,
    val childType: KClass<ChildT>,
) : Iterable<ChildT> {
    override fun iterator(): Iterator<ChildT> {
        return parent.getChildren(role).map {
            val wrapped = when (childConcept) {
                is GeneratedConcept<*, *> -> childConcept.wrap(it)
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
            childType.cast(wrapped)
        }.iterator()
    }

    fun addNew(index: Int = -1) {
        parent.addNewChild(role, index, childConcept)
    }

    fun remove(child: INode) {
        parent.removeChild(child)
    }

    fun remove(child: TypedNodeImpl) {
        remove(child.node)
    }
}