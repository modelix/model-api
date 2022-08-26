package org.modelix.metamodel.generator

fun newLanguage(name: String, body: LanguageBuilder.()->Unit): Language {
    return LanguageBuilder(name).apply(body).build()
}

class LanguageBuilder(val name: String) {
    private val concepts = ArrayList<Concept>()
    fun build(): Language {
        return Language(
            name = name,
            concepts = concepts
        )
    }

    fun concept(name: String, body: ConceptBuilder.()->Unit) {
        concepts.add(ConceptBuilder(name).apply(body).build())
    }
}

class ConceptBuilder(val conceptName: String) {
    private var abstract: Boolean = false
    private val properties: MutableList<Property> = ArrayList()
    private val children: MutableList<Child> = ArrayList()
    private val references: MutableList<Reference> = ArrayList()
    private val extends: MutableList<String> = ArrayList()

    fun abstract(value: Boolean = true) {
        abstract = value
    }

    fun property(name: String) {
        properties.add(Property(name))
    }

    fun reference(name: String, type: String, optional: Boolean = false) {
        references.add(Reference(name, type, optional))
    }

    fun optionalReference(name: String, type: String) {
        reference(name, type, true)
    }

    fun child(name: String, type: String, optional: Boolean, multiple: Boolean) {
        children.add(Child(name = name, type = type, multiple = multiple, optional = optional))
    }

    fun child0n(name: String, type: String) = child(name = name, type = type, optional = true, multiple = true)
    fun child1n(name: String, type: String) = child(name = name, type = type, optional = false, multiple = true)
    fun child0(name: String, type: String) = child(name = name, type = type, optional = true, multiple = false)
    fun child1(name: String, type: String) = child(name = name, type = type, optional = false, multiple = false)

    fun extends(type: String) {
        extends.add(type)
    }

    fun build(): Concept {
        return Concept(
            name = conceptName,
            abstract = abstract,
            properties = properties,
            children = children,
            references = references,
            extends = extends
        )
    }
}
