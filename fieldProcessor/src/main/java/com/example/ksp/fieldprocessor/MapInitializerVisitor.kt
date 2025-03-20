package com.example.ksp.fieldprocessor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.squareup.kotlinpoet.CodeBlock

class MapInitializerVisitor(
    private val codeBuilder: CodeBlock.Builder,
    private val logger: KSPLogger,
) : KSVisitorVoid() {

    private var isCustomFieldsDefined: Boolean = false

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        codeBuilder.add("mapOf(\n")

        classDeclaration
            .getAllProperties()
            .forEach { it.accept(this, Unit) }

        codeBuilder.add(")\n")
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
        property.annotations
            .forEach {
                when (it.shortName.asString()) {
                    FieldProcessor.fieldAnnotationName -> addFieldToMap(it, property)
                    FieldProcessor.customFieldsAnnotationName -> addCustomFields(property)
                }
            }
    }

    private fun addFieldToMap(
        ksAnnotation: KSAnnotation,
        property: KSPropertyDeclaration,
    ) {
        ksAnnotation.arguments
            .firstOrNull()
            ?.value
            ?.toString()
            ?.also { fieldName ->
                val propertyName = property.simpleName.asString()
                codeBuilder.addStatement("\"$fieldName\" to { $propertyName },")
            }
    }

    private fun addCustomFields(property: KSPropertyDeclaration) {
        if (isCustomFieldsDefined)
            logger.error(
                "You can declare only one property as container for custom fields",
                property
            )

        if (isMapStringString(property.type.resolve())) {
            isCustomFieldsDefined = true
            val propertyName = property.simpleName.asString()
            codeBuilder.addStatement("${FieldProcessor.CUSTOM_FIELDS_KEY} to { $propertyName },")
        } else {
            logger.error(
                "You can declare custom fields container only property with type Map<String, String>",
                property
            )
        }
    }

    private fun isMapStringString(type: KSType): Boolean {
        if (type.declaration.qualifiedName?.asString() != "kotlin.collections.Map") {
            return false // Not a Map
        }

        val typeArguments = type.arguments
        if (typeArguments.size != 2) {
            return false // Not a Map with two type arguments
        }

        val keyType = typeArguments[0].type?.resolve()
        if (keyType?.declaration?.qualifiedName?.asString() != "kotlin.String") {
            return false // Key is not String
        }

        val valueType = typeArguments[1].type?.resolve()
        if (valueType?.declaration?.qualifiedName?.asString() != "kotlin.String") {
            return false // Value is not String
        }

        return true // It's a Map<String, String>
    }
}
