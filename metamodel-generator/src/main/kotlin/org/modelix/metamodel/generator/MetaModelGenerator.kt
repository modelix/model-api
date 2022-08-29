package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.modelix.metamodel.*
import org.modelix.model.api.*
import java.nio.file.Path
import kotlin.reflect.KClass


class MetaModelGenerator(val outputDir: Path) {

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
            language.packageDir().toFile().listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            val builder = FileSpec.builder(language.generatedClassName().packageName, language.generatedClassName().simpleName)
            val file = builder.addType(generateLanguage(language)).build()
            for (concept in language.concepts) {
                generateConceptFile(language, concept)
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

    private fun generateConceptFile(language: Language, concept: Concept) {
        FileSpec.builder(language.name, concept.name)
            .addType(generateConceptObject(language, concept))
            .addType(generateConceptWrapperInterface(language, concept))
            .addType(generateConceptWrapperImpl(language, concept))
            .addType(generateNodeWrapperInterface(language, concept))
            .addType(generateNodeWrapperImpl(language, concept))
            .addImport(PropertyAccessor::class.asClassName().packageName, PropertyAccessor::class.asClassName().simpleName)
            .addImport(ReferenceAccessor::class.asClassName().packageName, ReferenceAccessor::class.asClassName().simpleName)
            .build().write()
    }

    private fun generateConceptObject(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.objectBuilder(concept.conceptObjectName()).apply {
            superclass(GeneratedConcept::class.asTypeName().parameterizedBy(
                ClassName(language.name, concept.nodeWrapperImplName()),
                ClassName(language.name, concept.conceptWrapperImplName())
            ))
            addSuperclassConstructorParameter(concept.abstract.toString())
            val instanceClassType = KClass::class.asClassName().parameterizedBy(ClassName(language.name, concept.nodeWrapperImplName()))
            addProperty(PropertySpec.builder("instanceClass", instanceClassType, KModifier.OVERRIDE)
                .initializer(concept.nodeWrapperImplName() + "::class")
                .build())
            addProperty(PropertySpec.builder("language", ILanguage::class, KModifier.OVERRIDE)
                .initializer(language.generatedClassName().simpleName)
                .build())
            addFunction(FunSpec.builder("wrap")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("node", INode::class)
                .addStatement("return ${concept.nodeWrapperImplName()}(node)")
                .build())
            addFunction(FunSpec.builder("getDirectSuperConcepts")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("return listOf(${concept.extends.joinToString(", ") { it.conceptObjectName() } })")
                .returns(List::class.asTypeName().parameterizedBy(IConcept::class.asTypeName()))
                .build())
            for (property in concept.properties) {
                addProperty(PropertySpec.builder(property.name, IProperty::class)
                    .initializer("""newProperty("${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, IReferenceLink::class)
                    .initializer("""newReferenceLink("${link.name}", ${link.optional}, ${link.type.conceptObjectName()})""")
                    .build())
            }
            for (link in concept.children) {
                addProperty(PropertySpec.builder(link.name, IChildLink::class)
                    .initializer("""newChildLink("${link.name}", ${link.multiple}, ${link.optional}, ${link.type.conceptObjectName()})""")
                    .build())
            }
        }.build()
    }

    private fun generateConceptWrapperInterface(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.interfaceBuilder(ClassName(language.name, concept.conceptWrapperImplName())).apply {
            //addProperty(PropertySpec.builder("concept", ClassName(language.name, concept.conceptObjectName()), KModifier.OVERRIDE).build())
            addSuperinterface(ITypedConcept::class)
            for (extended in concept.extends) {
                addSuperinterface(ClassName(language.name, extended.conceptWrapperImplName()))
            }
            for (property in concept.properties) {
                addProperty(PropertySpec.builder(property.name, IProperty::class).build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, IReferenceLink::class).build())
            }
            for (link in concept.children) {
                addProperty(PropertySpec.builder(link.name, IChildLink::class).build())
            }
        }.build()
    }

    private fun generateConceptWrapperImpl(language: Language, concept: Concept): TypeSpec {
        val ownCN = ClassName(language.name, concept.conceptWrapperInterfaceName())
        return TypeSpec.classBuilder(ownCN).apply {
            addModifiers(KModifier.OPEN)
            if (concept.extends.isEmpty()) {
            } else {
                superclass(ClassName(language.name, concept.extends.first().conceptWrapperInterfaceName()))
                for (extended in concept.extends.drop(1)) {
                    addSuperinterface(ClassName(language.name, extended.conceptWrapperImplName()), CodeBlock.of(extended.conceptWrapperInterfaceName() + ".INSTANCE"))
                }
            }
            addSuperinterface(ClassName(language.name, concept.conceptWrapperImplName()))

            primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PROTECTED).build())

            addProperty(PropertySpec.builder("concept", IConcept::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(concept.conceptObjectName())
                .build())

            for (property in concept.properties) {
                addProperty(PropertySpec.builder(property.name, IProperty::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(concept.conceptObjectName() + "." + property.name)
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, IReferenceLink::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(concept.conceptObjectName() + "." + link.name)
                    .build())
            }
            for (link in concept.children) {
                addProperty(PropertySpec.builder(link.name, IChildLink::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(concept.conceptObjectName() + "." + link.name)
                    .build())
            }

            addType(TypeSpec.companionObjectBuilder()
                .addProperty(PropertySpec.builder("INSTANCE", ownCN).initializer(ownCN.simpleName + "()").build())
                .build())
        }.build()
    }

    private fun generateNodeWrapperImpl(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.classBuilder(ClassName(language.name, concept.nodeWrapperImplName())).apply {
            addModifiers(KModifier.OPEN)
            val conceptType = ClassName(language.name, concept.conceptWrapperImplName())
            addProperty(PropertySpec.builder("concept", conceptType, KModifier.OVERRIDE)
                .initializer(concept.conceptWrapperInterfaceName() + ".INSTANCE")
                .build())

            if (concept.extends.size > 1) {
                // fix kotlin warning about ambiguity in case of multiple inheritance
                addProperty(PropertySpec.builder("node", INode::class, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return super.node").build())
                    .build())
            }

            primaryConstructor(FunSpec.constructorBuilder().addParameter("node", INode::class).build())
            if (concept.extends.isEmpty()) {
                superclass(TypedNodeImpl::class)
                addSuperclassConstructorParameter("node")
            } else {
                superclass(ClassName(language.name, concept.extends.first().nodeWrapperImplName()))
                addSuperclassConstructorParameter("node")
                for (extended in concept.extends.drop(1)) {
                    addSuperinterface(ClassName(language.name, extended.nodeWrapperInterfaceName()), CodeBlock.of(extended.nodeWrapperImplName() + "(node)"))
                }
            }
            addSuperinterface(ClassName(language.name, concept.nodeWrapperInterfaceName()))
            for (property in concept.properties) {
                val optionalString = String::class.asTypeName().copy(nullable = true)
                addProperty(PropertySpec.builder(property.name, optionalString)
                    .addModifiers(KModifier.OVERRIDE)
                    .mutable(true)
                    .delegate("""PropertyAccessor(node, "${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, ClassName(language.name, link.type.nodeWrapperInterfaceName()).copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .mutable(true)
                    .delegate("""ReferenceAccessor(node, "${link.name}", ${link.type.nodeWrapperInterfaceName()}::class)""")
                    .build())
            }
            for (link in concept.children) {
                // TODO resolve link.type and ensure it exists
                val type = ChildrenAccessor::class.asClassName()
                    .parameterizedBy(
                        ClassName(language.name, link.type.nodeWrapperInterfaceName()))
                val childConceptClassName = language.generatedClassName().nestedClass(link.type).canonicalName
                addProperty(PropertySpec.builder(link.name, type)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("""ChildrenAccessor(node, "${link.name}", $childConceptClassName, ${link.type.nodeWrapperInterfaceName()}::class)""")
                    .build())
            }
        }.build()
    }

    private fun generateNodeWrapperInterface(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.interfaceBuilder(ClassName(language.name, concept.nodeWrapperInterfaceName())).apply {
            //addProperty(PropertySpec.builder("concept", ClassName(language.name, concept.conceptObjectName()), KModifier.OVERRIDE).build())
            if (concept.extends.isEmpty()) addSuperinterface(ITypedNode::class.asTypeName())
            for (extended in concept.extends) {
                addSuperinterface(ClassName(language.name, extended.nodeWrapperInterfaceName()))
            }
            for (property in concept.properties) {
                val optionalString = String::class.asTypeName().copy(nullable = true)
                addProperty(PropertySpec.builder(property.name, optionalString)
                    .mutable(true)
                    //.delegate("""PropertyAccessor("${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, ClassName(language.name, link.type.nodeWrapperInterfaceName()).copy(nullable = true))
                    .mutable(true)
                    //.delegate("""ReferenceAccessor("${link.name}", ${link.type.instanceImplName()}::class)""")
                    .build())
            }
            for (link in concept.children) {
                // TODO resolve link.type and ensure it exists
                val type = ChildrenAccessor::class.asClassName()
                    .parameterizedBy(
                        ClassName(language.name, link.type.nodeWrapperInterfaceName()))
                val childConceptClassName = language.generatedClassName().nestedClass(link.type).canonicalName
                addProperty(PropertySpec.builder(link.name, type)
                    //.initializer("""ChildrenAccessor(node, "${link.name}", $childConceptClassName, ${link.type.instanceImplName()}::class)""")
                    .build())
            }
        }.build()
    }

    private fun Language.generatedClassName()  = ClassName(name, "L_" + name.replace(".", "_"))
    private fun Concept.nodeWrapperInterfaceName() = name.nodeWrapperInterfaceName()
    private fun String.nodeWrapperInterfaceName() = "N_" + this
    private fun Concept.nodeWrapperImplName() = name.nodeWrapperImplName()
    private fun String.nodeWrapperImplName() = "_N_" + this
    private fun Concept.conceptObjectName() = name.conceptObjectName()
    private fun String.conceptObjectName() = "_CC_" + this
    private fun Concept.conceptWrapperImplName() = name.conceptWrapperImplName()
    private fun String.conceptWrapperImplName() = "_C_" + this
    private fun Concept.conceptWrapperInterfaceName() = name.conceptWrapperInterfaceName()
    private fun String.conceptWrapperInterfaceName() = "C_" + this
}