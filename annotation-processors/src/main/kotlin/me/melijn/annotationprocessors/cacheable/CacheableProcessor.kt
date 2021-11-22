package me.melijn.annotationprocessors.cacheable

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

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
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.annotationprocessors.cacheable.Cacheable").toList()
        val ret = symbols.filter { !it.validate() }.toList()

        // (classDeclaration.declarations.first() as KSClassDeclaration).getAllFunctions().iterator()
        symbols
            .filter { symbol -> symbol is KSClassDeclaration && symbol.validate() }
            .forEach { symbol ->
                symbol.accept(InjectorVisitor(), Unit)
            }

        if (symbols.isNotEmpty()) {
            injectKoinModuleFile.close()
        }

        return ret
    }

    inner class InjectorVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.primaryConstructor!!.accept(this, data)
            classDeclaration
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val parent = function.parentDeclaration as KSClassDeclaration
            val className = parent.qualifiedName?.asString() ?: throw IllegalStateException("Annotation not on class ?")

            sb.append("fun $className.toCache(): ${parent.simpleName}Data {")
            sb.append("    return ${parent.simpleName}Data()")
            sb.append("}")
            //injectKoinModuleFile.appendText("single { $className(${function.parameters.joinToString(", ") { "get()" }}) } bind $className::class\n")
        }
    }
}
