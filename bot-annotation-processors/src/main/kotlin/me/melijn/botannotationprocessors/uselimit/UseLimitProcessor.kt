package me.melijn.botannotationprocessors.uselimit

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import kotlinx.datetime.Clock
import me.melijn.apredgres.util.Reflections.getIndexes
import me.melijn.apredgres.util.Reflections.getParametersFromProperties
import me.melijn.apredgres.util.Reflections.getSanitizedNameFromIndex
import me.melijn.apredgres.util.appendLine
import me.melijn.apredgres.util.appendText
import me.melijn.kordkommons.database.DriverManager
import org.intellij.lang.annotations.Language

class UseLimitProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
    val location: String
) : SymbolProcessor {

    /** For [UseLimit.TableType.LIMIT_HIT] type */
    val useLimitManagers: MutableSet<String> = mutableSetOf()
    val useLimitManagerFuncs: MutableList<String> = mutableListOf()

    val useLimitTypeFuncsMap: MutableMap<String, String> = mutableMapOf()

    val limitImports: MutableSet<String> = mutableSetOf(
        "${DriverManager::class.qualifiedName}",
        "me.melijn.bot.model.kordex.MelUsageHistory",
        "me.melijn.bot.database.manager.intoUsageHistory",
        "me.melijn.bot.database.manager.runQueriesForHitTypes",
        "org.jetbrains.exposed.sql.SqlExpressionBuilder.eq",
        "org.jetbrains.exposed.sql.SqlExpressionBuilder.less",
        "org.jetbrains.exposed.sql.and",
    )


    /** Maps sets of field names to their index getter name. Populated if [historyProcessed] == true */
    val fieldsToIndexGetter = mutableMapOf<Set<String>, String>()

    private val useLimitManager = codeGenerator.createNewFile(
        Dependencies(false),
        location, "UsageLimitHistoryManager"
    )

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
}
            """.trimIndent()
            useLimitManager.appendLine("package $location")
            useLimitManager.appendLine(limitImports.joinToString("\n") { "import $it" })
            useLimitManager.appendLine(useLimitManagerClass)
            useLimitManager.close()

            // Use limit types
            val limitTypes = codeGenerator.createNewFile(
                Dependencies(false),
                location, "PersistentUsageLimitType"
            )
            limitTypes.appendLine("package $location")
            limitTypes.appendLine(limitImports.joinToString("\n") { "import $it" })


            val body2 = useLimitTypeFuncsMap.entries.joinToString("\n") {
                @Language("kotlin")
                val t = """
    object ${it.key} : PersistentUsageLimitType() {
${it.value}
    }
                """
                t
            }

            @Language("kotlin")
            val limitTypesClass = """
import com.kotlindiscord.kord.extensions.usagelimits.CommandLimitType
import com.kotlindiscord.kord.extensions.usagelimits.DiscriminatingContext
import com.kotlindiscord.kord.extensions.usagelimits.cooldowns.CooldownHistory
import com.kotlindiscord.kord.extensions.usagelimits.ratelimits.RateLimitHistory
import kotlinx.datetime.Instant
import org.koin.core.component.inject

sealed class PersistentUsageLimitType : CommandLimitType {

    val usageLimitHistoryManager: UsageLimitHistoryManager by inject<>()
    
$body2
}
            """.trimIndent()
            limitTypes.appendLine(limitTypesClass)
            limitTypes.close()

        }
        return ret
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
            .groupBy({ it.type }, { it.moment.toEpochMilliseconds() })
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
            this[UserCommandUseLimitHistory.type] = type
            this[UserCommandUseLimitHistory.moment] = moment
        })
            """
            useLimitManagerFuncs.add(getter)
            useLimitManagerFuncs.add(setter)

            @Language("kotlin")
            val limitHitUsageHistoryFuncs = """
        override fun getCooldown(context: DiscriminatingContext): Instant {
     
        }
        
        override fun setCooldown(context: DiscriminatingContext, until: Instant) {
          
        }
        
        override fun getCooldownUsageHistory(context: DiscriminatingContext): CooldownHistory {
            val cd = usageLimitHistoryManager.get${funcId}History(${indexFields.joinToString(", ") { "context.$it" }})
            return cd
        }
        
        override fun getRateLimitUsageHistory(context: DiscriminatingContext): RateLimitHistory {
            TODO("Not yet implemented")
        }
        
        override fun setCooldownUsageHistory(context: DiscriminatingContext, usageHistory: CooldownHistory) {
             usageLimitHistoryManager.set${funcId}HistSerialized(${indexFields.joinToString(", ") { "context.$it" }}, usageHistory)
        }
        
        override fun setRateLimitUsageHistory(context: DiscriminatingContext, rateLimitHistory: RateLimitHistory) {
            TODO("Not yet implemented")
        }
            """
            useLimitTypeFuncsMap[funcId] = limitHitUsageHistoryFuncs
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

        }
    }
}