package org.modelix.metamodel.generator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Language(
    val name: String,
    val concepts: List<Concept>,
) {

    fun toYaml(): String = Yaml.default.encodeToString(this)
    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    companion object {
        private val prettyJson = Json { prettyPrint = true }
        fun fromFile(file: File): Language {
            return when (file.extension.lowercase()) {
                "yaml" -> Yaml.default.decodeFromString(file.readText())
                "json" -> Json.decodeFromString(file.readText())
                else -> throw IllegalArgumentException("Unsupported file extension: $file")
            }
        }
    }
}

@Serializable
data class Concept(
    val name: String,
    val abstract: Boolean = false,
    val properties: List<Property> = emptyList(),
    val children: List<Child> = emptyList(),
    val references: List<Reference> = emptyList(),
    val extends: List<String> = emptyList(),
)

interface IConceptFeature {
    val name: String
}

@Serializable
data class Property (
    override val name: String,
    val type: PropertyType = PropertyType.STRING
) : IConceptFeature

enum class PropertyType {
    STRING,
}

@Serializable
data class Child(
    override val name: String,
    val type: String,
    val multiple: Boolean = false,
    val optional: Boolean = true,
) : IConceptFeature

@Serializable
data class Reference(
    override val name: String,
    val type: String,
    val optional: Boolean = true,
) : IConceptFeature
