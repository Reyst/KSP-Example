package com.example.ksp.fieldprocessor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class FieldProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val classSymbols = resolver.getSymbolsWithAnnotation(classAnnotationName)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()


        classSymbols.forEach { ksClassDeclaration ->
            createFieldResolver(ksClassDeclaration)
        }
        return emptyList()
    }

    private fun createFieldResolver(ksClassDeclaration: KSClassDeclaration) {
        val packageName = ksClassDeclaration.packageName.asString()
        val className = ksClassDeclaration.toClassName() // Ім'я класу

        val resolverObjectName = ClassName(
            packageName = packageName,
            "${className.simpleName}$CLASS_NAME_SUFFIX"
        )

        val mapInitializer = getMapInitializer(ksClassDeclaration)

        val propertyMap = PropertySpec
            .builder(
                FIELD_MAP_NAME,
                MAP.parameterizedBy(
                    String::class.asClassName(),
                    LambdaTypeName.get(
                        receiver = className,
                        returnType = ANY.copy(nullable = true),
                    )
                )
            )
            .addModifiers(KModifier.PRIVATE)
            .initializer(mapInitializer)
            .build()

        val objFunction = FunSpec
            .builder(OBJ_FUNCTION_NAME)
            .addParameter("instance", className)
            .addParameter("fieldName", String::class)
            .returns(ANY.copy(nullable = true))
            .addStatement("return $FIELD_MAP_NAME[fieldName]?.invoke(instance)")
            .build()

        val resolverObject = TypeSpec
            .objectBuilder(resolverObjectName)
            .addFunction(objFunction)
            .addProperty(propertyMap)
            .build()

        val extensionFunction = FunSpec
            .builder(FUNCTION_NAME)
            .receiver(className)
            .addParameter("fieldName", String::class)
            .returns(ANY.copy(nullable = true))
            .addStatement("return ${resolverObjectName.simpleName}.$OBJ_FUNCTION_NAME(this, fieldName)")
            .build()

        val fileSpec1 = FileSpec
            .builder(
                packageName = packageName,
                fileName = "${className.simpleName}$CLASS_NAME_SUFFIX",
            )
            .addType(resolverObject)
            .build()

        val fileSpec2 = FileSpec
            .builder(
                packageName = packageName,
                fileName = "${className.simpleName}${CLASS_NAME_SUFFIX}Ext",
            )
            .addFunction(extensionFunction)
            .build()

            fileSpec1.writeTo(codeGenerator, false)
            fileSpec2.writeTo(codeGenerator, false)
    }

    private fun getMapInitializer(ksClassDeclaration: KSClassDeclaration): CodeBlock {

        val mapBuilder = CodeBlock.builder()
            .add("mapOf(\n")

        ksClassDeclaration.getAllProperties()
            .forEach { ksProperty ->

                val fieldName = ksProperty.annotations
                    .firstOrNull { it.shortName.asString() == fieldAnnotationName }
                    ?.arguments
                    ?.firstOrNull()
                    ?.value
                    ?.toString()

                if (fieldName != null) {
                    val propertyName = ksProperty.simpleName.asString()

                    mapBuilder.addStatement(
                        "\"$fieldName\" to { $propertyName },"
                    )
                }
            }

        return mapBuilder
            .add(")")
            .build()
    }

    companion object {
        internal val fieldAnnotationName = MetaField::class.asClassName().simpleName
        internal val classAnnotationName = HasMetadata::class.qualifiedName
            ?: error("Incorrect HasMetadata::class.qualifiedName")

        const val CLASS_NAME_SUFFIX = "FieldResolver"
        const val FUNCTION_NAME = "getMetaFieldValue"
        const val FIELD_MAP_NAME = "fieldGetters"
        const val OBJ_FUNCTION_NAME = "getFieldValue"
    }
}
