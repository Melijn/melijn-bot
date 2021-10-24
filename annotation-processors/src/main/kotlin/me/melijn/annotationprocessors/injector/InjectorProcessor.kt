package me.melijn.annotationprocessors.injector

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.appendText

class InjectorProcessor(
    codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    val injectKoinModuleFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.bot", "InjectorKoinModule")

    init {
        injectKoinModuleFile.appendText("package me.melijn.bot\n\n")
        injectKoinModuleFile.appendText(
            """
                import org.koin.dsl.bind
                import org.koin.dsl.module
               """.trimIndent())
        injectKoinModuleFile.appendText("\nobject InjectionKoinModule {\n\n")
        injectKoinModuleFile.appendText("    val module = module {\n")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.annotationprocessors.injector.Inject").toList()
        val ret = symbols.filter { !it.validate() }.toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(InjectorVisitor(), Unit) }

        if (symbols.isNotEmpty()) {
            injectKoinModuleFile.appendText("    }\n")
            injectKoinModuleFile.appendText("}\n")
            injectKoinModuleFile.close()
        }

        return ret
    }

    inner class InjectorVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.primaryConstructor!!.accept(this, data)
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val parent = function.parentDeclaration as KSClassDeclaration

            val className = parent.qualifiedName?.asString() ?: throw IllegalStateException("Annotation not on class ?")
            injectKoinModuleFile.appendText("         single { $className(${function.parameters.joinToString(", ") { "get()" }}) } bind $className::class\n")
        }
    }
}