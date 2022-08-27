package org.modelix.metamodel.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.modelix.metamodel.GeneratedConceptInstance
import org.modelix.metamodel.GeneratedConcept
import org.modelix.metamodel.GeneratedLanguage
import org.modelix.metamodel.NodeChildren
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
            builder.addProperty(PropertySpec.builder(concept.name, ClassName(language.name, concept.name))
                .initializer(language.name + "." + concept.name)
                .build())
        }
        return builder.build()
    }

    private fun generateConceptFile(language: Language, concept: Concept) {
        FileSpec.builder(language.name, concept.name)
            .addType(generateConceptObject(language, concept))
            .addType(generateConceptInstanceClass(language, concept))
            .build().write()
    }

    private fun generateConceptObject(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.objectBuilder(concept.name).apply {
            superclass(GeneratedConcept::class.asTypeName().parameterizedBy(ClassName(language.name, concept.name + "Instance")))
            addSuperclassConstructorParameter(concept.abstract.toString())
            val instanceClassType = KClass::class.asClassName().parameterizedBy(ClassName(language.name, concept.name + "Instance"))
            addProperty(PropertySpec.builder("instanceClass", instanceClassType, KModifier.OVERRIDE)
                .initializer(concept.name + "Instance::class")
                .build())
            addProperty(PropertySpec.builder("language", ILanguage::class, KModifier.OVERRIDE)
                .initializer(language.generatedClassName().simpleName)
                .build())
            addFunction(FunSpec.builder("wrap")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("node", INode::class)
                .addStatement("return ${concept.name}Instance(node)")
                .build())
            addFunction(FunSpec.builder("getDirectSuperConcepts")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement("return listOf(${concept.extends.joinToString(", ")})")
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
                    .initializer("""newChildLink("${link.name}", ${link.multiple}, ${link.optional}, ${link.type})""")
                    .build())
            }
        }.build()
    }

    private fun generateConceptInstanceClass(language: Language, concept: Concept): TypeSpec {
        return TypeSpec.classBuilder(ClassName(language.name, concept.name + "Instance")).apply {
            addProperty(PropertySpec.builder("concept", ClassName(language.name, concept.name), KModifier.OVERRIDE)
                .initializer(concept.name)
                .build())
            primaryConstructor(FunSpec.constructorBuilder().addParameter("node", INode::class).build())
            superclass(GeneratedConceptInstance::class)
            addSuperclassConstructorParameter("node")
            for (property in concept.properties) {
                val optionalString = String::class.asTypeName().copy(nullable = true)
                addProperty(PropertySpec.builder(property.name, optionalString)
                    .mutable(true)
                    .delegate("""PropertyAccessor("${property.name}")""")
                    .build())
            }
            for (link in concept.references) {
                addProperty(PropertySpec.builder(link.name, ClassName(language.name, link.type + "Instance").copy(nullable = true))
                    .mutable(true)
                    .delegate("""ReferenceAccessor("${link.name}", ${link.type}Instance::class)""")
                    .build())
            }
            for (link in concept.children) {
                // TODO resolve link.type and ensure it exists
                val type = NodeChildren::class.asClassName()
                    .parameterizedBy(
                        ClassName(language.name, concept.name + "Instance"),
                        ClassName(language.name, link.type + "Instance"))
                val childConceptClassName = language.generatedClassName().nestedClass(link.type).canonicalName
                addProperty(PropertySpec.builder(link.name, type)
                    .initializer("""NodeChildren(this, "${link.name}", $childConceptClassName, ${link.type}Instance::class)""")
                    .build())
            }
        }.build()
    }

    private fun Language.generatedClassName()  = ClassName(name, "L_" + name.replace(".", "_"))
}