package com.example.ksp.fieldprocessor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
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
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.UUID

class FieldProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classSymbols = resolver.getSymbolsWithAnnotation(classAnnotationName)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()

        classSymbols.forEach { ksClassDeclaration -> createFieldResolver(ksClassDeclaration) }
        return emptyList()
    }

    private fun createFieldResolver(ksClassDeclaration: KSClassDeclaration) {
        val ksFile = ksClassDeclaration.containingFile ?: return

        val packageName = ksClassDeclaration.packageName.asString()
        val className = ksClassDeclaration.toClassName()

        val propertyMap = createMapProperty(ksClassDeclaration, className)

        val constSpec = createCustomFieldKeyConstant()

        val objFunction = createFieldResolveFunction(className)

        val resolverObjectName = ClassName(
            packageName = packageName,
            "${className.simpleName}$CLASS_NAME_SUFFIX"
        )
        val resolverObject = createResolverObject(
            resolverObjectName = resolverObjectName,
            constSpec = constSpec,
            objFunction = objFunction,
            propertyMap = propertyMap,
        )

        val extensionFunction = createExtensionFunction(className, resolverObjectName)

        val fileSpec = FileSpec
            .builder(
                packageName = packageName,
                fileName = "${className.simpleName}$CLASS_NAME_SUFFIX",
            )
            .addType(resolverObject)
            .addFunction(extensionFunction)
            .build()

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(true, ksFile),
            packageName = packageName,
            fileName = "${className.simpleName}$CLASS_NAME_SUFFIX",
        )

        file.bufferedWriter().use { writer -> fileSpec.writeTo(writer) }
    }

    private fun createResolverObject(
        resolverObjectName: ClassName,
        constSpec: PropertySpec,
        objFunction: FunSpec,
        propertyMap: PropertySpec
    ): TypeSpec {
        val resolverObject = TypeSpec.objectBuilder(resolverObjectName)
            .addProperty(constSpec)
            .addFunction(objFunction)
            .addProperty(propertyMap)
            .build()
        return resolverObject
    }

    private fun createExtensionFunction(
        className: ClassName,
        resolverObjectName: ClassName
    ): FunSpec {
        val extensionFunction = FunSpec
            .builder(FUNCTION_NAME)
            .receiver(className)
            .addParameter(FIELD_NAME_PARAMETER, String::class)
            .returns(ANY.copy(nullable = true))
            .addStatement("return ${resolverObjectName.simpleName}.$OBJ_FUNCTION_NAME(this, fieldName)")
            .build()
        return extensionFunction
    }

    private fun createFieldResolveFunction(className: ClassName): FunSpec {
        val objFunction = FunSpec
            .builder(OBJ_FUNCTION_NAME)
            .addParameter(INSTANCE_PARAMETER, className)
            .addParameter(FIELD_NAME_PARAMETER, String::class)
            .returns(ANY.copy(nullable = true))
            .addStatement("")
            .addCode(
                """
                    return if (fieldGetters.containsKey(fieldName)) {
                          fieldGetters[fieldName]?.invoke(instance)
                        } else {
                          fieldGetters[CUSTOM_FIELDS_KEY]?.invoke(instance)
                            ?.let { it as? Map<*, *> }
                            ?.get(fieldName)
                        }
                    """.trimIndent()
            )
            .build()
        return objFunction
    }

    private fun createCustomFieldKeyConstant(): PropertySpec {
        val constSpec = PropertySpec
            .builder(CUSTOM_FIELDS_KEY, STRING)
            .addModifiers(KModifier.CONST)
            .addModifiers(KModifier.PRIVATE)
            .initializer("\"${UUID.randomUUID()}\"")
            .build()
        return constSpec
    }

    private fun createMapProperty(
        ksClassDeclaration: KSClassDeclaration,
        className: ClassName
    ): PropertySpec {
        val initializerBuilder = CodeBlock.builder()
        val visitor = MapInitializerVisitor(
            initializerBuilder,
            logger
        )
        ksClassDeclaration.accept(visitor, Unit)
        val mapInitializer = initializerBuilder.build()

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
        return propertyMap
    }

    companion object {
        internal val fieldAnnotationName = MetaField::class.asClassName().simpleName
        internal val customFieldsAnnotationName = CustomFields::class.asClassName().simpleName

        private val classAnnotationName = HasMetadata::class.qualifiedName
            ?: error("Incorrect HasMetadata::class.qualifiedName")

        private const val FIELD_NAME_PARAMETER = "fieldName"
        private const val INSTANCE_PARAMETER = "instance"

        const val CLASS_NAME_SUFFIX = "FieldResolver"
        const val FUNCTION_NAME = "getMetaFieldValue"
        const val FIELD_MAP_NAME = "fieldGetters"
        const val OBJ_FUNCTION_NAME = "getFieldValue"
        const val CUSTOM_FIELDS_KEY = "CUSTOM_FIELDS_KEY"
    }
}
