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
            .addType(generateConceptInstanceInterface(language, concept))
            .addType(generateConceptInstanceClass(language, concept))
            .addImport(PropertyAccessor::class.asClassName().packageName, PropertyAccessor::class.asClassName().simpleName)
            .addImport(ReferenceAccessor::class.asClassName().packageName, ReferenceAccessor::class.asClassName().simpleName)
            .build().write()
    }

    private fun generateConceptObject(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.objectBuilder(concept.conceptObjectName()).apply {
            superclass(GeneratedConcept::class.asTypeName().parameterizedBy(
                ClassName(language.name, concept.instanceImplName()),
                ClassName(language.name, concept.conceptInterfaceName())
            ))
            addSuperclassConstructorParameter(concept.abstract.toString())
            val instanceClassType = KClass::class.asClassName().parameterizedBy(ClassName(language.name, concept.instanceImplName()))
            addProperty(PropertySpec.builder("instanceClass", instanceClassType, KModifier.OVERRIDE)
                .initializer(concept.instanceImplName() + "::class")
                .build())
            addProperty(PropertySpec.builder("language", ILanguage::class, KModifier.OVERRIDE)
                .initializer(language.generatedClassName().simpleName)
                .build())
            addFunction(FunSpec.builder("wrap")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("node", INode::class)
                .addStatement("return ${concept.instanceImplName()}(node)")
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
                    .initializer("""newReferenceLink("${link.name}", ${link.optional}, ${link.type})""")
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
        return TypeSpec.interfaceBuilder(ClassName(language.name, concept.conceptInterfaceName())).apply {
            //addProperty(PropertySpec.builder("concept", ClassName(language.name, concept.conceptObjectName()), KModifier.OVERRIDE).build())
            addSuperinterface(IConceptWrapper::class)
            for (extended in concept.extends) {
                addSuperinterface(ClassName(language.name, extended.conceptInterfaceName()))
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
        return TypeSpec.classBuilder(ClassName(language.name, concept.conceptWrapperName())).apply {
            addModifiers(KModifier.OPEN)
            if (concept.extends.isEmpty()) {
            } else {
                superclass(ClassName(language.name, concept.extends.first().conceptWrapperName()))
                for (extended in concept.extends.drop(1)) {
                    addSuperinterface(ClassName(language.name, extended.conceptInterfaceName()), CodeBlock.of(extended.conceptWrapperName() + "()"))
                }
            }
            addSuperinterface(ClassName(language.name, concept.conceptInterfaceName()))

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
        }.build()
    }

    private fun generateConceptInstanceClass(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.classBuilder(ClassName(language.name, concept.instanceImplName())).apply {
            addModifiers(KModifier.OPEN)
            val conceptType =
                //ClassName(language.name, concept.conceptObjectName())
                IConcept::class
            addProperty(PropertySpec.builder("concept", conceptType, KModifier.OVERRIDE)
                .initializer(concept.conceptObjectName())
                .build())
            primaryConstructor(FunSpec.constructorBuilder().addParameter("node", INode::class).build())
            if (concept.extends.isEmpty()) {
                superclass(GeneratedConceptInstance::class)
                addSuperclassConstructorParameter("node")
            } else {
                superclass(ClassName(language.name, concept.extends.first().instanceImplName()))
                addSuperclassConstructorParameter("node")
                for (extended in concept.extends.drop(1)) {
                    addSuperinterface(ClassName(language.name, extended.instanceInterfaceName()), CodeBlock.of(extended.instanceImplName() + "(node)"))
                }
            }
            addSuperinterface(ClassName(language.name, concept.instanceInterfaceName()))
            for (property in concept.properties) {
                val optionalString = String::class.asTypeName().copy(nullable = true)
                addProperty(PropertySpec.builder(property.name, optionalString)
                    .addModifiers(KModifier.OVERRIDE)
                    .mutable(true)
                    .delegate("""PropertyAccessor(node, "${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, ClassName(language.name, link.type.instanceImplName()).copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .mutable(true)
                    .delegate("""ReferenceAccessor(node, "${link.name}", ${link.type.instanceImplName()}::class)""")
                    .build())
            }
            for (link in concept.children) {
                // TODO resolve link.type and ensure it exists
                val type = ChildrenAccessor::class.asClassName()
                    .parameterizedBy(
                        ClassName(language.name, link.type.instanceImplName()))
                val childConceptClassName = language.generatedClassName().nestedClass(link.type).canonicalName
                addProperty(PropertySpec.builder(link.name, type)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("""ChildrenAccessor(node, "${link.name}", $childConceptClassName, ${link.type.instanceImplName()}::class)""")
                    .build())
            }
        }.build()
    }


    private fun generateConceptInstanceInterface(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.interfaceBuilder(ClassName(language.name, concept.instanceInterfaceName())).apply {
            //addProperty(PropertySpec.builder("concept", ClassName(language.name, concept.conceptObjectName()), KModifier.OVERRIDE).build())
            //addSuperinterface(IGeneratedConceptInstance::class.asTypeName())
            for (extended in concept.extends) {
                addSuperinterface(ClassName(language.name, extended.instanceInterfaceName()))
            }
            for (property in concept.properties) {
                val optionalString = String::class.asTypeName().copy(nullable = true)
                addProperty(PropertySpec.builder(property.name, optionalString)
                    .mutable(true)
                    //.delegate("""PropertyAccessor("${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, ClassName(language.name, link.type.instanceImplName()).copy(nullable = true))
                    .mutable(true)
                    //.delegate("""ReferenceAccessor("${link.name}", ${link.type.instanceImplName()}::class)""")
                    .build())
            }
            for (link in concept.children) {
                // TODO resolve link.type and ensure it exists
                val type = ChildrenAccessor::class.asClassName()
                    .parameterizedBy(
                        ClassName(language.name, link.type.instanceImplName()))
                val childConceptClassName = language.generatedClassName().nestedClass(link.type).canonicalName
                addProperty(PropertySpec.builder(link.name, type)
                    //.initializer("""ChildrenAccessor(node, "${link.name}", $childConceptClassName, ${link.type.instanceImplName()}::class)""")
                    .build())
            }
        }.build()
    }

    private fun Language.generatedClassName()  = ClassName(name, "L_" + name.replace(".", "_"))
    private fun Concept.instanceInterfaceName() = name.instanceInterfaceName()
    private fun String.instanceInterfaceName() = "IN_" + this
    private fun Concept.instanceImplName() = name.instanceImplName()
    private fun String.instanceImplName() = "N_" + this
    private fun Concept.conceptObjectName() = name.conceptObjectName()
    private fun String.conceptObjectName() = "C_" + this
    private fun Concept.conceptInterfaceName() = name.conceptInterfaceName()
    private fun String.conceptInterfaceName() = "IC_" + this
    private fun Concept.conceptWrapperName() = name.conceptWrapperName()
    private fun String.conceptWrapperName() = "CW_" + this
}