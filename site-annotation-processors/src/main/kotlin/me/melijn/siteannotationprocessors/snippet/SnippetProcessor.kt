package me.melijn.siteannotationprocessors.snippet

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.siteannotationprocessors.util.appendLine

class SnippetProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    var count = 0

    var lines = mutableListOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val jClass = Snippet::class.java
        val symbols = resolver.getSymbolsWithAnnotation(jClass.name).toList()
        val ret = symbols.filter { !it.validate() }.toList()

        val process = symbols
            .filter { it is KSClassDeclaration && it.validate() }

        if (process.isNotEmpty()) {
            val injectKoinModuleFile =
                codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "Snippets${count}")

            injectKoinModuleFile.appendLine("package me.melijn.gen\n")
            injectKoinModuleFile.appendLine("import model.SnippetsInterface")
            injectKoinModuleFile.appendLine("class Snippets$count : SnippetsInterface {")
            injectKoinModuleFile.appendLine("    override val snippets = listOf(")

            process.forEach { it.accept(InjectorVisitor(lines), Unit) }
            injectKoinModuleFile.appendLine(lines.joinToString(",\n"))

            injectKoinModuleFile.appendLine("    )\n")
            injectKoinModuleFile.appendLine("}")
            injectKoinModuleFile.close()
            count++
        }


        return ret
    }


    inner class InjectorVisitor(private val lines: MutableList<String>) : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.primaryConstructor!!.accept(this, data)
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val parent = function.parentDeclaration as KSClassDeclaration

            val className = parent.qualifiedName?.asString() ?: throw IllegalStateException("Annotation not on class ?")
            lines.add("         $className()")
        }
    }
}