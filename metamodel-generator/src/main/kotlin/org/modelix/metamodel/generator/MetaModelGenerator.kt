package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.modelix.metamodel.*
import org.modelix.model.api.*
import java.nio.file.Path
import kotlin.reflect.KClass


class MetaModelGenerator(val outputDir: Path) {
    private val languagesMap = HashMap<String, Language>()
    private val conceptsMap = HashMap<String, ConceptInLanguage>()

    private fun FileSpec.write() {
        writeTo(outputDir)
    }

    private fun Language.packageDir(): Path {
        val packageName = name
        var packageDir = outputDir
        if (packageName.isNotEmpty()) {
            for (packageComponent in packageName.split('.').dropLastWhile { it.isEmpty() }) {
                packageDir = packageDir.resolve(packageComponent)
            }
        }
        return packageDir
    }

    fun generate(languages: List<Language>) {
        for (language in languages) {
            languagesMap[language.name] = language
            for (concept in language.getConceptsInLanguage()) {
                conceptsMap[concept.getConceptFqName()] = concept
            }
        }

        for (language in languages) {
            language.packageDir().toFile().listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            val builder = FileSpec.builder(language.generatedClassName().packageName, language.generatedClassName().simpleName)
            val file = builder.addType(generateLanguage(language)).build()
            for (concept in language.getConceptsInLanguage()) {
                generateConceptFile(concept)
            }
            file.write()
        }
    }

    private fun generateLanguage(language: Language): TypeSpec {
        val builder = TypeSpec.objectBuilder(language.generatedClassName())
        val conceptNamesList = language.concepts.joinToString(", ") { it.name }
        builder.addFunction(FunSpec.builder("getConcepts")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return listOf($conceptNamesList)")
            .build())
        builder.superclass(GeneratedLanguage::class)
        builder.addSuperclassConstructorParameter("\"${language.name}\"")
        for (concept in language.concepts) {
            builder.addProperty(PropertySpec.builder(concept.name, ClassName(language.name, concept.conceptObjectName()))
                .initializer(language.name + "." + concept.conceptObjectName())
                .build())
        }
        return builder.build()
    }

    private fun generateConceptFile(concept: ConceptInLanguage) {
        FileSpec.builder(concept.language.name, concept.concept.name)
            .addType(generateConceptObject(concept))
            .addType(generateConceptWrapperInterface(concept))
            .addType(generateConceptWrapperImpl(concept))
            .addType(generateNodeWrapperInterface(concept))
            .addType(generateNodeWrapperImpl(concept))
            .addImport(PropertyAccessor::class.asClassName().packageName, PropertyAccessor::class.asClassName().simpleName)
            .addImport(ReferenceAccessor::class.asClassName().packageName, ReferenceAccessor::class.asClassName().simpleName)
            .build().write()
    }

    private fun generateConceptObject(concept: ConceptInLanguage): TypeSpec {
        return TypeSpec.objectBuilder(concept.conceptObjectName()).apply {
            superclass(GeneratedConcept::class.asTypeName().parameterizedBy(
                concept.nodeWrapperImplType(),
                concept.conceptWrapperImplType()
            ))
            addSuperclassConstructorParameter(concept.concept.abstract.toString())
            val instanceClassType = KClass::class.asClassName().parameterizedBy(concept.nodeWrapperImplType())
            addProperty(PropertySpec.builder("instanceClass", instanceClassType, KModifier.OVERRIDE)
                .initializer(concept.nodeWrapperImplName() + "::class")
                .build())
            addProperty(PropertySpec.builder("language", ILanguage::class, KModifier.OVERRIDE)
                .initializer(concept.language.generatedClassName().simpleName)
                .build())
            addFunction(FunSpec.builder("wrap")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("node", INode::class)
                .addStatement("return ${concept.nodeWrapperImplName()}(node)")
                .build())
            addFunction(FunSpec.builder("getDirectSuperConcepts")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("return listOf(${concept.concept.extends.joinToString(", ") { it.conceptObjectName() } })")
                .returns(List::class.asTypeName().parameterizedBy(IConcept::class.asTypeName()))
                .build())
            for (property in concept.concept.properties) {
                addProperty(PropertySpec.builder(property.name, IProperty::class)
                    .initializer("""newProperty("${property.name}")""")
                    .build())
            }
            for (link in concept.concept.references) {
                addProperty(PropertySpec.builder(link.name, IReferenceLink::class)
                    .initializer("""newReferenceLink("${link.name}", ${link.optional}, ${link.type.conceptObjectName()})""")
                    .build())
            }
            for (link in concept.concept.children) {
                addProperty(PropertySpec.builder(link.name, IChildLink::class)
                    .initializer("""newChildLink("${link.name}", ${link.multiple}, ${link.optional}, ${link.type.conceptObjectName()})""")
                    .build())
            }
        }.build()
    }

    private fun generateConceptWrapperInterface(concept: ConceptInLanguage): TypeSpec {
        return TypeSpec.interfaceBuilder(concept.conceptWrapperImplType()).apply {
            addSuperinterface(ITypedConcept::class)
            for (extended in concept.extended()) {
                addSuperinterface(extended.conceptWrapperImplType())
            }
            for (property in concept.concept.properties) {
                addProperty(PropertySpec.builder(property.name, IProperty::class).build())
            }
            for (link in concept.concept.references) {
                addProperty(PropertySpec.builder(link.name, IReferenceLink::class).build())
            }
            for (link in concept.concept.children) {
                addProperty(PropertySpec.builder(link.name, IChildLink::class).build())
            }
        }.build()
    }

    private fun generateConceptWrapperImpl(concept: ConceptInLanguage): TypeSpec {
        return TypeSpec.classBuilder(concept.conceptWrapperInterfaceType()).apply {
            addModifiers(KModifier.OPEN)
            if (concept.extends().isEmpty()) {
            } else {
                superclass(concept.extends().first().conceptWrapperInterfaceType())
                for (extended in concept.extends().drop(1)) {
                    addSuperinterface(extended.conceptWrapperImplType(), CodeBlock.of(extended.conceptWrapperInterfaceType().canonicalName + ".INSTANCE"))
                }
            }
            addSuperinterface(concept.conceptWrapperImplType())

            primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PROTECTED).build())

            addProperty(PropertySpec.builder(ITypedConcept::_concept.name, IConcept::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(concept.conceptObjectName())
                .build())

            for (feature in concept.directFeaturesAndConflicts()) {
                when (val data = feature.data) {
                    is Property -> {
                        addProperty(PropertySpec.builder(data.name, IProperty::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                    is Child -> {
                        addProperty(PropertySpec.builder(data.name, IChildLink::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                    is Reference -> {
                        addProperty(PropertySpec.builder(data.name, IReferenceLink::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(feature.kotlinRef())
                            .build())
                    }
                }
            }

            addType(TypeSpec.companionObjectBuilder()
                .addProperty(PropertySpec.builder("INSTANCE", concept.conceptWrapperInterfaceType())
                    .initializer(concept.conceptWrapperInterfaceType().simpleName + "()").build())
                .build())
        }.build()
    }

    private fun generateNodeWrapperImpl(concept: ConceptInLanguage): TypeSpec {
        return TypeSpec.classBuilder(concept.nodeWrapperImplType()).apply {
            addModifiers(KModifier.OPEN)
            addProperty(PropertySpec.builder(TypedNodeImpl::_concept.name, concept.conceptWrapperImplType(), KModifier.OVERRIDE)
                .initializer(concept.conceptWrapperInterfaceType().simpleName + ".INSTANCE")
                .build())

            if (concept.extends().size > 1) {
                // fix kotlin warning about ambiguity in case of multiple inheritance
                addProperty(PropertySpec.builder(ITypedNode::_node.name, INode::class, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return super.${ITypedNode::_node.name}").build())
                    .build())
            }

            primaryConstructor(FunSpec.constructorBuilder().addParameter(ITypedNode::_node.name, INode::class).build())
            if (concept.extends().isEmpty()) {
                superclass(TypedNodeImpl::class)
                addSuperclassConstructorParameter(ITypedNode::_node.name)
            } else {
                superclass(concept.extends().first().nodeWrapperImplType())
                addSuperclassConstructorParameter(ITypedNode::_node.name)
                for (extended in concept.extends().drop(1)) {
                    addSuperinterface(extended.nodeWrapperInterfaceType(), CodeBlock.of(extended.nodeWrapperImplType().canonicalName + "(" + ITypedNode::_node.name + ")"))
                }
            }
            addSuperinterface(concept.nodeWrapperInterfaceType())
            for (feature in concept.directFeaturesAndConflicts()) {
                when (val data = feature.data) {
                    is Property -> {
                        val optionalString = String::class.asTypeName().copy(nullable = true)
                        addProperty(PropertySpec.builder(data.name, optionalString)
                            .addModifiers(KModifier.OVERRIDE)
                            .mutable(true)
                            .delegate("""PropertyAccessor(${ITypedNode::_node.name}, "${data.name}")""")
                            .build())
                    }
                    is Child -> {
                        // TODO resolve link.type and ensure it exists
                        val type = ChildrenAccessor::class.asClassName()
                            .parameterizedBy(
                                data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType())
                        addProperty(PropertySpec.builder(data.name, type)
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer("""ChildrenAccessor(${ITypedNode::_node.name}, "${data.name}", ${data.type.conceptObjectName()}, ${data.type.nodeWrapperInterfaceName()}::class)""")
                            .build())
                    }
                    is Reference -> {
                        addProperty(PropertySpec.builder(data.name, data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType().copy(nullable = true))
                            .addModifiers(KModifier.OVERRIDE)
                            .mutable(true)
                            .delegate("""ReferenceAccessor(${ITypedNode::_node.name}, "${data.name}", ${data.type.nodeWrapperInterfaceName()}::class)""")
                            .build())
                    }
                }
            }
        }.build()
    }

    private fun generateNodeWrapperInterface(concept: ConceptInLanguage): TypeSpec {
        return TypeSpec.interfaceBuilder(concept.nodeWrapperInterfaceType()).apply {
            if (concept.extends().isEmpty()) addSuperinterface(ITypedNode::class.asTypeName())
            for (extended in concept.extends()) {
                addSuperinterface(extended.nodeWrapperInterfaceType())
            }
            for (feature in concept.directFeatures()) {
                when (val data = feature.data) {
                    is Property -> {
                        val optionalString = String::class.asTypeName().copy(nullable = true)
                        addProperty(PropertySpec.builder(data.name, optionalString)
                            .mutable(true)
                            .build())
                    }
                    is Child -> {
                        // TODO resolve link.type and ensure it exists
                        val type = ChildrenAccessor::class.asClassName()
                            .parameterizedBy(
                                data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType())
                        addProperty(PropertySpec.builder(data.name, type)
                            .build())
                    }
                    is Reference -> {
                        addProperty(PropertySpec.builder(data.name, data.type.parseConceptRef(concept.language).nodeWrapperInterfaceType().copy(nullable = true))
                            .mutable(true)
                            .build())
                    }
                }
            }
        }.build()
    }

    private fun Language.generatedClassName()  = ClassName(name, "L_" + name.replace(".", "_"))
    private fun Concept.nodeWrapperInterfaceName() = name.nodeWrapperInterfaceName()
    private fun String.nodeWrapperInterfaceName() = fqNamePrefix("N_")
    private fun Concept.nodeWrapperImplName() = name.nodeWrapperImplName()
    private fun String.nodeWrapperImplName() = fqNamePrefix("_N_")
    private fun Concept.conceptObjectName() = name.conceptObjectName()
    private fun String.conceptObjectName() = fqNamePrefix("_CC_")
    private fun Concept.conceptWrapperImplName() = name.conceptWrapperImplName()
    private fun String.conceptWrapperImplName() = fqNamePrefix("_C_")
    private fun Concept.conceptWrapperInterfaceName() = name.conceptWrapperInterfaceName()
    private fun String.conceptWrapperInterfaceName() = fqNamePrefix("C_")
    private fun String.fqNamePrefix(prefix: String): String {
        return if (this.contains(".")) {
            this.substringBeforeLast(".") + "." + prefix + this.substringAfterLast(".")
        } else {
            prefix + this
        }
    }

    private inner class ConceptInLanguage(val concept: Concept, val language: Language) {
        /**
         * Unknown concepts are not included!
         */
        private val resolvedDirectSuperConcepts: List<ConceptInLanguage> by lazy {
            concept.extends.map { it.parseConceptRef(language) }.mapNotNull { conceptsMap[it.toString()] }
        }
        fun getConceptFqName() = language.name + "." + concept.name
        fun conceptObjectName() = concept.conceptObjectName()
        fun conceptObjectType() = ClassName(language.name, concept.conceptObjectName())
        fun nodeWrapperImplName() = concept.nodeWrapperImplName()
        fun nodeWrapperImplType() = ClassName(language.name, concept.nodeWrapperImplName())
        fun nodeWrapperInterfaceType() = ClassName(language.name, concept.nodeWrapperInterfaceName())
        fun conceptWrapperImplType() = ClassName(language.name, concept.conceptWrapperImplName())
        fun conceptWrapperInterfaceType() = ClassName(language.name, concept.conceptWrapperInterfaceName())
        fun extended(): List<ConceptRef> = concept.extends.map { it.parseConceptRef(language) }
        fun extends() = extended()
        fun resolveMultipleInheritanceConflicts(): Map<ConceptInLanguage, ConceptInLanguage> {
            val inheritedFrom = LinkedHashMap<ConceptInLanguage, MutableSet<ConceptInLanguage>>()
            for (superConcept in resolvedDirectSuperConcepts) {
                loadInheritance(superConcept, inheritedFrom)
            }
            return inheritedFrom.filter { it.value.size > 1 }.map { it.key to it.value.first() }.toMap()
        }
        fun allSuperConcepts(): List<ConceptInLanguage> =
            resolvedDirectSuperConcepts.flatMap { listOf(it) + it.allSuperConcepts() }.distinct()
        fun directFeatures(): List<FeatureInConcept> = (concept.properties + concept.children + concept.references)
            .map { FeatureInConcept(this, it) }
        fun allFeatures(): List<FeatureInConcept> = allSuperConcepts().flatMap { it.directFeatures() }.distinct()
        fun directFeaturesAndConflicts(): List<FeatureInConcept> =
            (directFeatures() + resolveMultipleInheritanceConflicts().flatMap { it.key.allFeatures() })
                .distinct().groupBy { it.data.name }.values.map { it.first() }
        fun ref() = ConceptRef(language.name, concept.name)
        fun loadInheritance(directSuperConcept: ConceptInLanguage, inheritedFrom: MutableMap<ConceptInLanguage, MutableSet<ConceptInLanguage>>) {
            for (superConcept in resolvedDirectSuperConcepts) {
                inheritedFrom.computeIfAbsent(superConcept, { LinkedHashSet() }).add(directSuperConcept)
                superConcept.loadInheritance(directSuperConcept, inheritedFrom)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ConceptInLanguage

            if (concept != other.concept) return false
            if (language != other.language) return false

            return true
        }

        override fun hashCode(): Int {
            var result = concept.hashCode()
            result = 31 * result + language.hashCode()
            return result
        }
    }

    private data class FeatureInConcept(val concept: ConceptInLanguage, val data: IConceptFeature) {
        fun kotlinRef() = concept.conceptObjectType().canonicalName + "." + CodeBlock.of("%N", data.name)
    }

    private inner class ConceptRef(val languageName: String, val conceptName: String) {
        init {
            require(!conceptName.contains(".")) { "Simple name expected for concept: $conceptName" }
        }
        override fun toString(): String = languageName + "." + conceptName

        fun conceptWrapperImplType() = ClassName(languageName, conceptName.conceptWrapperImplName())
        fun conceptWrapperInterfaceType() = ClassName(languageName, conceptName.conceptWrapperInterfaceName())
        fun nodeWrapperImplType() = ClassName(languageName, conceptName.nodeWrapperImplName())
        fun nodeWrapperInterfaceType() = ClassName(languageName, conceptName.nodeWrapperInterfaceName())
    }
    private fun String.parseConceptRef(contextLanguage: Language): ConceptRef {
        return if (this.contains(".")) {
            ConceptRef(this.substringBeforeLast("."), this.substringAfterLast("."))
        } else {
            ConceptRef(contextLanguage.name, this)
        }
    }
    private fun Language.getConceptsInLanguage() = concepts.map { ConceptInLanguage(it, this) }
}