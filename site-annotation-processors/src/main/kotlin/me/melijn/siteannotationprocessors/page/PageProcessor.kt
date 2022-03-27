package me.melijn.siteannotationprocessors.page

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.siteannotationprocessors.util.appendLine

class PageProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    var count = 0

    var lines = mutableListOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val jClass = Page::class.java
        val symbols = resolver.getSymbolsWithAnnotation(jClass.name).toList()
        val ret = symbols.filter { !it.validate() }.toList()

        val process = symbols
            .filter { it is KSClassDeclaration && it.validate() }

        if (process.isNotEmpty()) {
            val injectKoinModuleFile =
                codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "Pages${count}")

            injectKoinModuleFile.appendLine("package me.melijn.gen\n")
            injectKoinModuleFile.appendLine("import model.PageInterface")
            injectKoinModuleFile.appendLine("class Pages$count : PageInterface {")
            injectKoinModuleFile.appendLine("    override val pages = listOf(")

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