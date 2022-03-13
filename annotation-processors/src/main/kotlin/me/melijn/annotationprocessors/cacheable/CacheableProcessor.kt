package me.melijn.annotationprocessors.cacheable

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.innerArguments
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.appendLine
import me.melijn.annotationprocessors.util.appendText
import java.io.OutputStream

class CacheableProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    val injectKoinModuleFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "CacheExtensions")

    val sb = StringBuilder()

    init {
        sb.append("package me.melijn.gen\n\n")
        sb.appendLine("""
                import kotlinx.serialization.*
                import kotlinx.serialization.json.*
            """.trimIndent())
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.bot.database.model.Cacheable").toList()
        val ret = symbols.filter { !it.validate() }.toList()

        // (classDeclaration.declarations.first() as KSClassDeclaration).getAllFunctions().iterator()
        symbols
            .filter { symbol -> symbol is KSClassDeclaration && symbol.validate() }
            .forEach { symbol ->
                symbol.accept(InjectorVisitor(resolver), Unit)
            }

        if (symbols.isNotEmpty()) {
            injectKoinModuleFile.appendText(sb.toString())
            injectKoinModuleFile.close()
        }

        return ret
    }

    inner class InjectorVisitor(private val resolver: Resolver) : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val daoName = classDeclaration.packageName.asString() + "." + classDeclaration.simpleName.asString()
            val model = classDeclaration.annotations.first {
                it.shortName.asString() == "Cacheable"
            }.annotationType.resolve().arguments[0].type?.resolve()?.declaration ?: return

            val exposedDataClass = model as KSClassDeclaration
            val name = exposedDataClass.packageName.asString() + "." + exposedDataClass.simpleName.asString()
            val simpleName = model.simpleName.getShortName()

            val settings = resolver.getClassDeclarationByName(object : KSName {
                override fun asString(): String = name
                override fun getQualifier(): String = exposedDataClass.packageName.asString()
                override fun getShortName(): String = exposedDataClass.simpleName.getShortName()
            }) ?: throw IllegalStateException("SUSSY BAKA ALTERT")

            val properties = settings.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }

            val pkeyProperty: KSPropertyDeclaration = settings.getDeclaredProperties().first {
                it.type.resolve().toString() == "PrimaryKey"
            }

            val FIELD = pkeyProperty.javaClass.getDeclaredField("propertyDescriptor\$delegate")
            FIELD.isAccessible = true
            val lazyPropertyDesciptor = FIELD.get(pkeyProperty) // as kotlin.Lazy<org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl>
            val lazyValueMethod = Lazy::class.java.getMethod("getValue")

            val lazyValue = lazyValueMethod.invoke(lazyPropertyDesciptor)
            // lazyPropertyDesciptor::class.get.getField("value")
            val propertyDescriptor = lazyValue
            val aaaa = lazyValue::class.java.getMethod("getSource").invoke(propertyDescriptor)
            val fish = aaaa::class.java.getMethod("getPsi").invoke(aaaa)
            val lastChildField = fish::class.java.getMethod("getLastChild")
            val firstChildField = fish::class.java.getMethod("getFirstChild")
            val last1 = lastChildField.invoke(fish)
            val last2 = lastChildField.invoke(last1)
            var invokeEl = firstChildField.invoke(last2)

            val fieldList = mutableListOf<String>()
            while (invokeEl != null) {
                val nextSiblingField = invokeEl::class.java.getMethod("getNextSibling")
                val type = invokeEl.toString()
                if (type == "VALUE_ARGUMENT") {
                    val first2 = firstChildField.invoke(invokeEl)
                    val first3 = firstChildField.invoke(first2)
                    val text = first3::class.java.getMethod("getText").invoke(first3)
                    fieldList.add(text.toString())
                }
                invokeEl = try { nextSiblingField.invoke(invokeEl) } catch (t: Throwable) { null }
            }
            val pkeyProperties = settings.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }
                .filter { fieldList.contains(it.simpleName.asString()) }

            sb.appendLine("// $fieldList")
            // last last first next first first
//            sb.appendLine("fun $daoName.toCache(): ${simpleName}Data {")
//            sb.appendLine("    return ${simpleName}Data(${properties.joinToString { getParam(it) }})")
//            sb.appendLine("}")

            /** Cache object class **/
            sb.appendLine("@Serializable")
            sb.appendLine("data class ${simpleName}Data(")
            sb.appendLine(properties.joinToString(",\n") {
//                "    @JsonProperty(\"${it.simpleName.asString()}\")\n" +
                "    var " + it.simpleName.asString() + ": " + getType(it)
            })
            sb.appendLine(") {")
//            sb.appendLine("    companion object {")

            /** from function **/
//            sb.appendLine("        fun from(oldData: ${simpleName}Data): ${simpleName}Data {")
//            sb.append("            return ${simpleName}Data(")
//            sb.append(properties.joinToString(", ") {
//                "oldData." + it.simpleName.asString()
//            })
//            sb.appendLine(")")
//            sb.appendLine("        }")


//            sb.appendLine("    }")

            /** spacer **/
            sb.appendLine()

            /** modifiable fields **/
//            sb.appendLine(properties.joinToString("\n") {
//                "    @JsonProperty(\"_${it.simpleName.asString()}\")\n" +
//                "    var " + it.simpleName.asString() + ": " + getType(it) + " = this._" + it.simpleName.asString()
//            })

            /** end of cache class **/
            sb.appendLine("}")

            val abstractPkg = "me.melijn.gen.database.manager"
            val abstractMgrName = "Abstract${simpleName}Manager"
            val dependencies = Dependencies(false)
            val abstractManager = codeGenerator.createNewFile(dependencies, abstractPkg, abstractMgrName)

            /** Do abstract mgr imports **/
            abstractManager.appendLine(
                """
                package $abstractPkg
                
                import com.fasterxml.jackson.databind.ObjectMapper
                import com.fasterxml.jackson.module.kotlin.readValue
                import me.melijn.bot.database.DBTableManager
                import me.melijn.bot.database.insertOrUpdate
                import me.melijn.bot.database.DriverManager
                import me.melijn.gen.${simpleName}Data
                import org.jetbrains.exposed.sql.select
                import org.jetbrains.exposed.sql.and
                import $daoName
                import org.koin.java.KoinJavaComponent.inject
                import kotlinx.serialization.*
                import kotlinx.serialization.json.*
                
            """.trimIndent()
            )
            abstractManager.appendLine("open class Abstract${simpleName}Manager(override val driverManager: DriverManager) : DBTableManager<${simpleName}>(driverManager, ${simpleName}) {")
            abstractManager.appendLine("")
            abstractManager.appendLine("    val objectMapper by inject<ObjectMapper>(ObjectMapper::class.java)")
            abstractManager.appendLine("")
            addGetByIdMethod(abstractManager, pkeyProperties, simpleName, name, properties)
            addStoreMethod(abstractManager, pkeyProperties, simpleName, name, properties)
            addGetCachedByIdMethod(abstractManager, pkeyProperties, simpleName)
            abstractManager.appendLine("}")
            abstractManager.close()
        }

        private fun addStoreMethod(
            abstractManager: OutputStream,
            pkeyProperties: Sequence<KSPropertyDeclaration>,
            simpleName: String,
            name: String,
            properties: Sequence<KSPropertyDeclaration>
        ) {
            abstractManager.appendLine("    fun store(data: ${simpleName}Data) {")

            val propertyNames = properties.map { it.simpleName.asString() }
            val pkeyNames = pkeyProperties.map { it.simpleName.asString() }
            val propertyNoKeys = propertyNames.filterNot { pkeyNames.contains(it) }
            val pkeyKeyPart = pkeyNames.joinToString(":") { "\${data.$it}" } // ${id1}:${id2}:...
            abstractManager.appendLine("        val key = \"melijn:${simpleName.lowercase()}:${pkeyKeyPart}\"")
            abstractManager.appendLine("        scopedTransaction {")
            abstractManager.appendLine("            ${simpleName}.insertOrUpdate({")
            abstractManager.appendLine(propertyNames.joinToString("\n") { "                it[${simpleName}.${it}] = data.${it}" })
            abstractManager.appendLine("            }, {")
            abstractManager.appendLine(propertyNoKeys.joinToString("\n") { "                it[${simpleName}.${it}] = data.${it}" })
            abstractManager.appendLine("            })")
            abstractManager.appendLine("        }")
            abstractManager.appendLine("        val cachableStr = Json.encodeToString(data)")
            abstractManager.appendLine("        driverManager.setCacheEntry(key, cachableStr, 5)")
            abstractManager.appendLine("    }")
        }

        private fun addGetCachedByIdMethod(
            abstractManager: OutputStream,
            pkeyProperties: Sequence<KSPropertyDeclaration>,
            simpleName: String
        ) {
            val pKeyParams = getPrimaryIdParameters(pkeyProperties)
            abstractManager.appendLine("    suspend fun getCachedById($pKeyParams): me.melijn.gen.${simpleName}Data? {")
            val pkeyNames = pkeyProperties.map { it.simpleName.asString() }

            val pkeyKeyPart = pkeyNames.joinToString(":") { "\${$it}" } // ${id1}:${id2}:...
            abstractManager.appendLine("        val key = \"melijn:${simpleName.lowercase()}:${pkeyKeyPart}\"")
            abstractManager.appendLine("        driverManager.getCacheEntry(key)?.run {")
            abstractManager.appendLine("            return  Json.decodeFromString<${simpleName}Data>(this)")
            abstractManager.appendLine("        }")
            abstractManager.appendLine("        val cachable = getById(${pkeyNames.joinToString(", ")})")
            abstractManager.appendLine("        val cachableStr = Json.encodeToString(cachable)")
            abstractManager.appendLine("        driverManager.setCacheEntry(key, cachableStr, 5)")
            abstractManager.appendLine("        return cachable")
            abstractManager.appendLine("    }")
        }

        private fun addGetByIdMethod(
            abstractManager: OutputStream,
            pkeyProperties: Sequence<KSPropertyDeclaration>,
            simpleName: String,
            name: String,
            properties: Sequence<KSPropertyDeclaration>
        ) {
            val pKeyParams = getPrimaryIdParameters(pkeyProperties)
            abstractManager.appendLine("    fun getById(${pKeyParams}): me.melijn.gen.${simpleName}Data? {")
            abstractManager.appendLine("        return scopedTransaction {")
            abstractManager.appendLine("             ${name}.select {")
            abstractManager.appendLine("                 ${pkeyProperties.joinToString(".and") { "(${name}.$it.eq($it))" }}")
            abstractManager.appendLine("             }.firstOrNull()?.let {")
            abstractManager.appendLine("                 me.melijn.gen.${simpleName}Data(")
            val paramList = properties.joinToString(",\n") {
                "                    it[${simpleName}.${it.simpleName.asString()}]${getValueGetter(it)}"
            }
            abstractManager.appendLine(paramList)
            abstractManager.appendLine("                 )")
            abstractManager.appendLine("             }")
            abstractManager.appendLine("         }")
            abstractManager.appendLine("    }")
        }

        private fun getPrimaryIdParameters(pkeyProperties: Sequence<KSPropertyDeclaration>) =
            pkeyProperties.joinToString(", ") {
                it.simpleName.asString() + ": " + getType(
                    it
                )
            }

        private fun getValueGetter(pd: KSPropertyDeclaration): String {
            if (pd.type.resolve().innerArguments.firstOrNull()?.type?.resolve()?.declaration?.simpleName?.asString() == "EntityID") {
                return ".value"
            }
            return ""
        }

        private fun getType(pd: KSPropertyDeclaration): String {
            val innerColumnType = pd.type.resolve().innerArguments.firstOrNull()?.type
            if (innerColumnType?.resolve()?.declaration?.simpleName?.asString() == "EntityID") {
                return innerColumnType.resolve().innerArguments.firstOrNull()?.type.toString()
            }
            return innerColumnType.toString()
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
        }
    }
}