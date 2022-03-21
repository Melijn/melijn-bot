package me.melijn.annotationprocessors.settings

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.Reflections
import me.melijn.annotationprocessors.util.appendText
import java.util.*

class SettingsProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    private val settingsFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "Settings")
    val settingsImports = StringBuilder()
    val settings = StringBuilder()

    init {
        settingsImports.appendLine("package me.melijn.gen\n")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.bot.config.SettingsTemplate").toList()

        settings.appendLine("object Settings {")
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { ksAnnotated ->
                settingsImports.appendLine(
                    """
                    import me.melijn.bot.model.Environment
                    import me.melijn.kordkommons.environment.BotSettings
                """.trimIndent()
                )
                ksAnnotated.accept(SettingVisitor(), Unit)
            }


        val ret = symbols.filter { !it.validate() }.toList()

        if (symbols.isNotEmpty()) {
            settings.appendLine("}")
            settingsFile.appendText(settingsImports.toString() + "\n" + settings.toString())
        }
        return ret
    }

    inner class SettingVisitor : KSVisitorVoid() {

        @Suppress("UNCHECKED_CAST") // how does one check a cast then
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val classes =
                classDeclaration.declarations.filter { it is KSClassDeclaration } as Sequence<KSClassDeclaration>
            var body = ""
            for (child in classes) {
                body += visitChildClassDeclaration(child, data)
            }

            val fields = getFieldsString(classDeclaration)

            settings.appendLine(body)
            settings.appendLine(fields)
        }

        @Suppress("UNCHECKED_CAST") // how does one check a cast then
        fun visitChildClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): String {
            val classes =
                classDeclaration.declarations.filter { it is KSClassDeclaration } as Sequence<KSClassDeclaration>
            var body = ""
            for (child in classes) {
                val innerBody = visitChildClassDeclaration(child, data)
                body += innerBody
            }
            return if (classes.count() == 0) {
                val code = Reflections.getCode(classDeclaration)
                code.replace("Declarations for info for ", "\n")
            } else {
                val variables = getFieldsString(classDeclaration)
                val className = classDeclaration.simpleName.asString()
                val configName = className.lowercase()
                "class $className : BotSettings(\"$configName\") {\n" +
                    body.replace("BotSettings\\(\"(\\w+)\"\\)".toRegex()) { res ->
                        "BotSettings(\"${configName}_${res.groups[1]!!.value}\")"
                    } + variables + "\n}"

            }
        }

        private fun getFieldsString(classDeclaration: KSClassDeclaration): String {
            return classDeclaration.declarations.joinToString("\n") { clazz ->
                val className = clazz.simpleName.asString()
                val variableName = className.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                if (variableName == "<init>") ""
                else "val $variableName = $className()"
            }
        }
    }

}