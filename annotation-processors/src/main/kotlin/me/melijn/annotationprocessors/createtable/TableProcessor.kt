package me.melijn.annotationprocessors.createtable

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import me.melijn.annotationprocessors.util.appendText

class TableProcessor(
    codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor{

    val createTablesModuleFile =
        codeGenerator.createNewFile(Dependencies(false), "me.melijn.gen", "CreateTablesModule")

    init {
        createTablesModuleFile.appendText("package me.melijn.gen\n\n")
        createTablesModuleFile.appendText(
            """
                import org.jetbrains.exposed.sql.SchemaUtils
               """.trimIndent()
        )
        createTablesModuleFile.appendText("\nobject CreateTablesModule {\n\n")
        createTablesModuleFile.appendText("    fun createTables() {\n")
        createTablesModuleFile.appendText("        SchemaUtils.create(\n")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("me.melijn.annotationprocessors.createtable.CreateTable").toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(CreateTableVisitor(), Unit) }
        val ret = symbols.filter { !it.validate() }.toList()

        if (symbols.isNotEmpty()) {
            createTablesModuleFile.appendText("        )\n")
            createTablesModuleFile.appendText("    }\n")
            createTablesModuleFile.appendText("}\n")
            createTablesModuleFile.close()
        }
        return ret
    }

    inner class CreateTableVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val className = classDeclaration.qualifiedName?.asString()?: throw IllegalStateException("Annotation not on class ?")
            createTablesModuleFile.appendText("            $className,\n")
        }
    }
}