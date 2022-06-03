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
            val snippetsFile =
                codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "Snippets${count}")

            snippetsFile.appendLine("package me.melijn.gen\n")
            snippetsFile.appendLine("import model.SnippetsInterface")
            snippetsFile.appendLine("class Snippets$count : SnippetsInterface {")
            snippetsFile.appendLine("    override val snippets = listOf(")

            process.forEach { it.accept(InjectorVisitor(lines), Unit) }
            snippetsFile.appendLine(lines.joinToString(",\n"))

            snippetsFile.appendLine("    )\n")
            snippetsFile.appendLine("}")
            snippetsFile.close()
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