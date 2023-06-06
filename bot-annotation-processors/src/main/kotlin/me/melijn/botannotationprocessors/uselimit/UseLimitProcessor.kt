package me.melijn.botannotationprocessors.uselimit

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import kotlinx.datetime.Clock
import me.melijn.apredgres.util.Reflections
import me.melijn.apredgres.util.Reflections.getIndexes
import me.melijn.apredgres.util.Reflections.getParametersFromProperties
import me.melijn.apredgres.util.Reflections.getSanitizedNameFromIndex
import me.melijn.apredgres.util.Reflections.getType
import me.melijn.apredgres.util.appendLine
import me.melijn.apredgres.util.appendText
import me.melijn.kordkommons.database.DriverManager
import org.intellij.lang.annotations.Language

class UseLimitProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
    val location: String
) : SymbolProcessor {

    /** CooldownManager */
    val cooldownManagers: MutableSet<String> = mutableSetOf()
    val cooldownManagerFuncs: MutableList<String> = mutableListOf()

    /** For [UseLimit.TableType.LIMIT_HIT] type */
    val useLimitManagers: MutableSet<String> = mutableSetOf()
    val useLimitManagerFuncs: MutableList<String> = mutableListOf()

    val useLimitTypeFuncsMap: MutableMap<String, String> = mutableMapOf()

    val limitTypeImports: MutableSet<String> = mutableSetOf()
    val cooldownImports: MutableSet<String> = mutableSetOf(
        "${DriverManager::class.qualifiedName}",
    )
    val limitImports: MutableSet<String> = mutableSetOf(
        "${DriverManager::class.qualifiedName}",
        "me.melijn.bot.model.kordex.MelUsageHistory",
        "me.melijn.bot.database.manager.intoUsageHistory",
        "me.melijn.bot.database.manager.runQueriesForHitTypes",
        "org.jetbrains.exposed.sql.SqlExpressionBuilder.eq",
        "org.jetbrains.exposed.sql.SqlExpressionBuilder.less",
        "org.jetbrains.exposed.sql.and",
    )

    val module = codeGenerator.createNewFile(
        Dependencies(false),
        location, "CommandLimitModule"
    )

    /** Maps sets of field names to their index getter name. Populated if [historyProcessed] == true */
    val fieldsToIndexGetter = mutableMapOf<Set<String>, String>()

    init {
        module.appendLine("package $location")
        module.appendLine((cooldownImports + limitImports).joinToString("\n") { "import $it" })
        module.appendLine("import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent;import org.koin.core.component.get;import org.koin.core.parameter.ParametersHolder;")
        module.appendLine("""
            import org.koin.dsl.bind
            import org.koin.dsl.module
        """.trimIndent())
        module.appendLine("object CommandLimitModule {")
        module.appendLine("fun getModule() = module {")
    }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(UseLimit::class.java.name).toList()
        val ret = symbols.filter { !it.validate() }.toList()

        val relevant = symbols.filter { it is KSClassDeclaration && it.validate() }
            .groupBy { it.getAnnotationsByType(UseLimit::class).first().tableType }

        // Process history table first to get indexes
        val history = relevant[UseLimit.TableType.HISTORY]?.firstOrNull() ?: return emptyList()
        history.accept(UsageTableVisitor(), Unit)

        relevant.flatMap { it.value }
            .forEach { symbol ->
                val useLimits = symbol.getAnnotationsByType(UseLimit::class)
                val visitor = when (useLimits.first().tableType) {
                    UseLimit.TableType.HISTORY -> return@forEach
                    UseLimit.TableType.COOLDOWN -> CooldownTableVisitor()
                    UseLimit.TableType.LIMIT_HIT -> LimitHitTableVisitor()
                }

                // Processing here ..
                symbol.accept(visitor, Unit)
            }

        if (symbols.isNotEmpty()) {
            createUseLimitClass()
            createCooldownManagerClass()

            // Use limit types
            createLimitTypesClass()
            finishLoadModule()
        }
        return ret
    }

    private fun finishLoadModule() {

        module.appendLine("     single { CooldownManager(${buildList {
            repeat(cooldownManagers.size) {
                add("get()")
            }
        }.joinToString()}) } bind CooldownManager::class")
        module.appendLine("     single { UsageHistoryManager(get()) } bind UsageHistoryManager::class")
        module.appendLine("     single { UsageLimitHistoryManager(${buildList { 
            repeat(useLimitManagers.size) {
                add("get()")
            }
        }.joinToString()}) } bind UsageLimitHistoryManager::class")
        module.appendLine("}")
        module.appendLine("}")
        module.close()
    }

    private fun createCooldownManagerClass() {
        val params =
            cooldownManagers.joinToString(",\n") { manager ->
                "    val ${manager.replaceFirstChar { it.lowercase() }}: $manager"
            }
        val body = cooldownManagerFuncs.joinToString("")

        @Language("kotlin")
        val cooldownManagerClass = """
class CooldownManager(
$params
) {
$body
}"""

        val cooldownManager = codeGenerator.createNewFile(
            Dependencies(false),
            location, "CooldownManager"
        )

        cooldownManager.appendLine("package $location")
        cooldownManager.appendLine(cooldownImports.joinToString("\n") { "import $it" })
        cooldownManager.appendLine(cooldownManagerClass)
        cooldownManager.close()
    }

    private fun createUseLimitClass() {
        val params =
            useLimitManagers.joinToString(",\n") { manager ->
                "    val ${manager.replaceFirstChar { it.lowercase() }}: $manager"
            }
        val body = useLimitManagerFuncs.joinToString("")

        @Language("kotlin")
        val useLimitManagerClass = """
class UsageLimitHistoryManager(
$params
) {
$body
}"""

        val useLimitManager = codeGenerator.createNewFile(
            Dependencies(false),
            location, "UsageLimitHistoryManager"
        )

        useLimitManager.appendLine("package $location")
        useLimitManager.appendLine(limitImports.joinToString("\n") { "import $it" })
        useLimitManager.appendLine(useLimitManagerClass)
        useLimitManager.close()
    }

    private fun createLimitTypesClass() {
        val limitTypes = codeGenerator.createNewFile(
            Dependencies(false),
            location, "PersistentUsageLimitType"
        )
        limitTypes.appendLine("package $location")
        limitTypes.appendLine(limitTypeImports.joinToString("\n") { "import $it" })

        val body2 = useLimitTypeFuncsMap.entries.joinToString("\n") {
            @Language("kotlin")
            val t = """
    object ${it.key} : PersistentUsageLimitType() {
${it.value}
    }"""
            t
        }

        @Language("kotlin")
        val limitTypesClass = """
import com.kotlindiscord.kord.extensions.usagelimits.CommandLimitType
import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitHistory
import kotlinx.datetime.Instant
import me.melijn.bot.utils.KoinUtil.inject
import ${location}.CooldownManager

sealed class PersistentUsageLimitType : CommandLimitType {

    val usageLimitHistoryManager: UsageLimitHistoryManager by inject()
    val cooldownManager: CooldownManager by inject()
    val emptyHistory = MelUsageHistory(emptyList(), emptyList(), false, emptyList())

    
    val DiscriminatingContext.userId: Long 
        get() = this.user.idLong
    val DiscriminatingContext.channelId: Long
        get() = this.channel.idLong
    val DiscriminatingContext.commandId
        get() = this.event.command.getFullName().hashCode()

$body2
}
                """
        limitTypes.appendLine(limitTypesClass)
        limitTypes.close()
    }

    inner class LimitHitTableVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val simpleName = classDeclaration.simpleName.asString()
            limitImports.add(classDeclaration.qualifiedName!!.asString())

            val managerName = "${simpleName}Manager"
            val managerFieldName = managerName.replaceFirstChar { c -> c.lowercase() }
            val abstractManagerName = "Abstract$managerName"

            val file = codeGenerator.createNewFile(
                Dependencies(false),
                location, managerName
            )

            useLimitManagers.add(managerName)
            @Language("kotlin")
            val clazz = """
package $location
import me.melijn.gen.database.manager.$abstractManagerName
import ${DriverManager::class.qualifiedName}

class $managerName(override val driverManager: DriverManager) : $abstractManagerName(driverManager)
            """.trimIndent()
            file.appendText(clazz)
            file.close()

            module.appendLine("    single { $managerName(get()) } bind $managerName::class")

            val indexFields = getIndexes(classDeclaration).first().fields

            val indexProps = classDeclaration.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }
                .filter { indexFields.contains(it.simpleName.asString()) }

            val indexGetter = fieldsToIndexGetter[indexFields.toSet()]
            val funcId = indexFields.joinToString("") {
                it.removeSuffix("Id").replaceFirstChar { c -> c.uppercase() }
            }
            val indexFieldsAsArgs = indexFields.joinToString(", ")
            val params = getParametersFromProperties(indexProps)

            @Language("kotlin")
            val getter = """
    /** (${indexFieldsAsArgs}) use limit history scope **/
    fun get${funcId}History(${params}): MelUsageHistory {
        val usageEntries = usageHistoryManager.getBy${funcId}Key(${indexFieldsAsArgs})
        val limitHitEntries = ${managerFieldName}.${indexGetter}(${indexFieldsAsArgs})
            .groupBy({ it.type }, { it.moment })
        return intoUsageHistory(usageEntries, limitHitEntries)
    }
            """

            @Language("kotlin")
            val setter = """
    /** (${indexFieldsAsArgs}) use limit history scope **/
    fun set${funcId}HistSerialized(${params}, usageHistory: MelUsageHistory) =
        usageHistoryManager.runQueriesForHitTypes(usageHistory, $simpleName, { moment, type ->
            ($simpleName.moment less moment) and
                    ($simpleName.type eq type) and
${indexFields.joinToString(" and\n") { " ".repeat(20) + "($simpleName.$it eq $it)" }}
        }, { moment, type ->
${indexFields.joinToString("\n") { " ".repeat(12) + "this[$simpleName.$it] = $it" }}
            this[$simpleName.type] = type
            this[$simpleName.moment] = moment
        })
            """
            useLimitManagerFuncs.add(getter)
            useLimitManagerFuncs.add(setter)

            limitTypeImports.add("me.melijn.bot.model.kordex.MelUsageHistory")

            var indexFieldsFromContextAsArgs = indexFields.joinToString(", ") { "context.$it" }

            val properties = classDeclaration.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }

            // Handle nullable guildId fields
            val safeGuildId = properties.firstOrNull {
                it.simpleName.asString() == "guildId" &&
                        !getType(it, true, removeNullable = false).endsWith("?")
            }?.let {
                indexFieldsFromContextAsArgs = indexFieldsFromContextAsArgs.replace("context.guildId", "guildId")
                "context.guildId?.let { guildId -> \n                " to "\n            } ?: emptyHistory"
            } ?: ("" to "")


            @Language("kotlin")
            val limitHitUsageHistoryFuncs = """
        override suspend fun getCooldownUsageHistory(context: DiscriminatingContext): CooldownHistory {
            return ${safeGuildId.first}usageLimitHistoryManager.get${funcId}History(${indexFieldsFromContextAsArgs})${safeGuildId.second}
        }

        override suspend fun setCooldownUsageHistory(context: DiscriminatingContext, usageHistory: CooldownHistory) {
             ${safeGuildId.first}usageLimitHistoryManager.set${funcId}HistSerialized(${indexFieldsFromContextAsArgs}, usageHistory as MelUsageHistory)${safeGuildId.second}
        }

        override suspend fun getRateLimitUsageHistory(context: DiscriminatingContext): RateLimitHistory {
            return ${safeGuildId.first}usageLimitHistoryManager.get${funcId}History(${indexFieldsFromContextAsArgs})${safeGuildId.second}
        }
        
        override suspend fun setRateLimitUsageHistory(context: DiscriminatingContext, rateLimitHistory: RateLimitHistory) {
            ${safeGuildId.first}usageLimitHistoryManager.set${funcId}HistSerialized(${indexFieldsFromContextAsArgs}, rateLimitHistory as MelUsageHistory)${safeGuildId.second}
        }
            """
            useLimitTypeFuncsMap[funcId] = (useLimitTypeFuncsMap[funcId] ?: "") + limitHitUsageHistoryFuncs
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {}
    }

    inner class UsageTableVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val simpleName = classDeclaration.simpleName.asString()
            val name = classDeclaration.qualifiedName!!.asString()

            val managerName = "${simpleName}Manager"
            val abstractManagerName = "Abstract$managerName"

            val file = codeGenerator.createNewFile(
                Dependencies(false),
                location, managerName
            )

            useLimitManagers.add(managerName)
            @Language("kotlin")
            val clazz = """
package $location                

import me.melijn.gen.database.manager.$abstractManagerName
import me.melijn.gen.${simpleName}Data
import ${DriverManager::class.qualifiedName}
import ${Clock::class.qualifiedName}
import $name
import net.dv8tion.jda.api.entities.ISnowflake
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

class $managerName(override val driverManager: DriverManager) : $abstractManagerName(driverManager) {

    /** Usage history tracker **/
    fun updateUsage(guildId: Long?, channelId: ISnowflake, userId: ISnowflake, commandId: Int) {
        val moment = Clock.System.now()
        scopedTransaction {
            store(
                UsageHistoryData(
                    guildId,
                    channelId.idLong,
                    userId.idLong,
                    commandId,
                    moment
                )
            )

            ${simpleName}.deleteWhere {
                (${simpleName}.userId eq userId.idLong) and (${simpleName}.moment less moment)
            }
        }
    }
}
            """.trimIndent()
            file.appendText(clazz)
            file.close()

            val indices = getIndexes(classDeclaration)

            for ((i, index) in indices.withIndex()) {
                fieldsToIndexGetter[index.fields.toSet()] = "getBy" + getSanitizedNameFromIndex(index, i + 1)
            }
        }
    }


    inner class CooldownTableVisitor : KSVisitorVoid() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val simpleName = classDeclaration.simpleName.asString()
            limitImports.add(classDeclaration.qualifiedName!!.asString())

            val managerName = "${simpleName}Manager"
            val managerFieldName = managerName.replaceFirstChar { c -> c.lowercase() }
            val abstractManagerName = "Abstract$managerName"

            val file = codeGenerator.createNewFile(
                Dependencies(false),
                location, managerName
            )

            cooldownManagers.add(managerName)
            @Language("kotlin")
            val clazz = """
package $location
import me.melijn.gen.database.manager.$abstractManagerName
import ${DriverManager::class.qualifiedName}

class $managerName(override val driverManager: DriverManager) : $abstractManagerName(driverManager)
            """.trimIndent()
            file.appendText(clazz)
            file.close()

            module.appendLine("    single { $managerName(get()) } bind $managerName::class")

            val pkeyProperty: KSPropertyDeclaration = classDeclaration.getDeclaredProperties().first {
                it.type.resolve().toString() == "PrimaryKey"
            }
            val indexFields = Reflections.getFields(pkeyProperty)

            val indexProps = classDeclaration.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }
                .filter { indexFields.contains(it.simpleName.asString()) }

            val funcId = indexFields.joinToString("") {
                it.removeSuffix("Id").replaceFirstChar { c -> c.uppercase() }
            }
            val indexFieldsAsArgs = indexFields.joinToString(", ")
            val params = getParametersFromProperties(indexProps)


            @Language("kotlin")
            val getter = """
    /** (${indexFieldsAsArgs}) **/
    suspend fun get${funcId}Cd(${params}): ${funcId}CooldownData? {
        return ${managerFieldName}.getById(${indexFieldsAsArgs})
    }
            """

            @Language("kotlin")
            val setter = """
    fun store${funcId}Cd(data: ${funcId}CooldownData) {
        ${managerFieldName}.store(data)
    }
            """

            cooldownImports.add("me.melijn.gen.${funcId}CooldownData")
            limitTypeImports.add("me.melijn.gen.${funcId}CooldownData")

            cooldownManagerFuncs.add(getter)
            cooldownManagerFuncs.add(setter)


            var indexFieldsFromContextAsArgs = indexFields.joinToString(", ") { "context.$it" }

            val properties = classDeclaration.getDeclaredProperties()
                .filter { it.simpleName.asString() != "primaryKey" }

            var dataArgs = properties.joinToString(", ") {
                val expression = when (val prop = it.simpleName.asString()) {
                    "until" -> "until.toEpochMilliseconds()"
                    else -> "context.${prop}"
                }
                expression
            }

            // Handle nullable guildId fields
            val safeGuildId = properties.firstOrNull {
                it.simpleName.asString() == "guildId" &&
                        !getType(it, true, removeNullable = false).endsWith("?")
            }?.let {
                indexFieldsFromContextAsArgs = indexFieldsFromContextAsArgs.replace("context.guildId", "guildId")
                dataArgs = dataArgs.replace("context.guildId", "guildId")
                "context.guildId?.let { guildId -> \n                " to "\n            }"
            } ?: ("" to "")

            @Language("kotlin")
            val limitHitUsageHistoryFuncs = """
        override suspend fun getCooldown(context: DiscriminatingContext): Instant {
            val res = ${safeGuildId.first}cooldownManager.get${funcId}Cd($indexFieldsFromContextAsArgs)?.until ${safeGuildId.second}?: 0
            return Instant.fromEpochMilliseconds(res)
        }
        
        override suspend fun setCooldown(context: DiscriminatingContext, until: Instant) {
            ${safeGuildId.first}cooldownManager.store${funcId}Cd(${funcId}CooldownData($dataArgs))${safeGuildId.second}
        }
            """
            useLimitTypeFuncsMap[funcId] = (useLimitTypeFuncsMap[funcId] ?: "") + limitHitUsageHistoryFuncs
        }
    }
}
