import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.appendLine
import me.melijn.annotationprocessors.util.appendText
import java.util.*

fepackage me.melijn.annotationprocessors.settings

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.Reflections
import me.melijn.annotationprocessors.util.appendLine
import me.melijn.annotationprocessors.util.appendText
import java.util.*

class SettingsProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    private val settingsFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "Settings")


    init {
        settingsFile.appendText("package me.melijn.gen\n\n")
        settingsFile.appendText(
            """
                
            """.trimIndent()
        )
        settingsFile.appendText("\nobject Settings {\n\n")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.bot.config.SettingsTemplate").toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(CreateTableVisitor(), Unit) }
        val ret = symbols.filter { !it.validate() }.toList()

        if (symbols.isNotEmpty()) {
            settingsFile.appendText("}\n")
            settingsFile.close()
        }
        return ret
    }

    inner class CreateTableVisitor : KSVisitorVoid() {

        @Suppress("UNCHECKED_CAST") // how does one check a cast then
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val classes =
                classDeclaration.declarations.filter { it is KSClassDeclaration } as Sequence<KSClassDeclaration>
            var body = ""
            for (child in classes) {
                body += visitChildClassDeclaration(child, data)
            }

            val fields = classDeclaration.declarations.joinToString("\n") { clazz ->
                val className = clazz.simpleName.asString()
                val variableName = className.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                "val $variableName = $className()"
            }

            settingsFile.appendLine(body)
            settingsFile.appendLine(fields)
        }

        @Suppress("UNCHECKED_CAST") // how does one check a cast then
        fun visitChildClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): String {
            val classes =
                classDeclaration.declarations.filter { it is KSClassDeclaration } as Sequence<KSClassDeclaration>
            var body = ""
            for (child in classes) {
                val childBody = visitChildClassDeclaration(child, data)
                body += childBody
            }
            if (classes.count() == 0) {

            }

            classDeclaration.declarations.joinToString("\n") { clazz ->
                val className = clazz.simpleName.asString()
                val variableName = className.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                "val $variableName = $className()"
            }


            settingsFile.appendLine("//" + classDeclaration.declarations.joinToString { it.simpleName.asString() })
        }
    }

}