package me.melijn.annotationprocessors.cacheable

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.innerArguments
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.appendText

class CacheableProcessor(
    codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    val injectKoinModuleFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.bot", "CacheExtensions")

    val sb = StringBuilder()

    init {
        sb.append("package me.melijn.bot\n\n")
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

    inner class InjectorVisitor(val resolver: Resolver) : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val daoName = classDeclaration.packageName.asString() + "." + classDeclaration.simpleName.asString()
            val model = classDeclaration.annotations.first {
                it.shortName.asString() == "Cacheable"
            }.annotationType.resolve().arguments[0].type?.resolve()?.declaration ?: return
            val exposedDataClass = model as KSClassDeclaration
            val name = exposedDataClass.packageName.asString() + "." + exposedDataClass.simpleName.asString()
            val simpleName = model.simpleName.getShortName()


//            class GuildSettingData(
//                private val _guildId: ULong,
//                private val _prefixes: String,
//                private val _allowNsfw: Boolean
//            ) : CacheableData {
//
//                var prefixes: String = _prefixes
//                var allowNsfw: Boolean = _allowNsfw
//
//                override fun getId(): String {
//                    return _guildId.toString()
//                }
//            }
            val settings = resolver.getClassDeclarationByName(object : KSName {
                override fun asString(): String = name
                override fun getQualifier(): String = exposedDataClass.packageName.asString()
                override fun getShortName(): String = exposedDataClass.simpleName.getShortName()
            }) ?: throw IllegalStateException("SUSSY BAKA ALTERT")

            val properties = settings.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }

            sb.appendLine("fun $daoName.toCache(): ${simpleName}Data {")
            sb.appendLine("    return ${simpleName}Data(${properties.joinToString { getParam(it) }})")
            sb.appendLine("}")
            sb.appendLine("""
                class ${simpleName}Data(${(
                    properties.joinToString(",\n") {
                        "val _" + it.simpleName.asString() + ": " + getType(it)
                    })}
                ) {
                    companion object {
                        fun from(oldData: ${simpleName}Data): ${simpleName}Data {
                            return ${simpleName}Data(${
                                properties.joinToString(", ") {
                                    "oldData." + it.simpleName.asString()
                                }
                            })
                        }
                    }
                    
                    ${
                        properties.joinToString("\n") {
                            "    var " + it.simpleName.asString() + ": " + getType(it) + " = this._" + it.simpleName.asString()
                    }}
                }
            """.trimIndent())
        }

        private fun getParam(pd: KSPropertyDeclaration): String {
            var base = "this." + pd.simpleName.asString()
            if (pd.type.resolve().innerArguments.firstOrNull()?.type?.resolve()?.declaration?.simpleName?.asString() == "EntityID") {
                base += ".value"
            }
            return base
        }

        fun getType(pd: KSPropertyDeclaration): String {
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