package me.melijn.bot.database.manager

import kotlinx.datetime.Instant
import me.melijn.bot.database.model.UseLimitHitType
import me.melijn.bot.model.kordex.MelUsageHistory
import me.melijn.gen.UsageHistoryData
import me.melijn.kordkommons.database.DBTableManager
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.statements.BatchInsertStatement

/** utils **/
fun intoUsageHistory(
    entries: List<UsageHistoryData>,
    limitHitEntries: Map<UseLimitHitType, List<Instant>>
): MelUsageHistory {
    val usageHistory = entries.map { it.moment }


    return MelUsageHistory(
        limitHitEntries[UseLimitHitType.COOLDOWN] ?: emptyList(),
        limitHitEntries[UseLimitHitType.RATELIMIT] ?: emptyList(),
        false,
        usageHistory,
    )
}

suspend fun <T : Table> DBTableManager<*>.runQueriesForHitTypes(
    usageHistory: MelUsageHistory,
    table: T,
    deleteFunc: T.(moment: Instant, type: UseLimitHitType) -> Op<Boolean>,
    insertFunc: BatchInsertStatement.(moment: Instant, type: UseLimitHitType) -> Unit,
) {
    scopedTransaction {
        val changes = usageHistory.changes
        for (type in UseLimitHitType.values()) {
            val (added, limit) = when (type) {
                UseLimitHitType.COOLDOWN -> changes.crossedCooldownsChanges
                UseLimitHitType.RATELIMIT -> changes.crossedLimitChanges
            }
            if (limit != null) {
                val moment = limit

                // Deletes expired entries for this scope
                table.deleteWhere {
                    deleteFunc(moment, type)
                }
            }
            table.batchInsert(added, shouldReturnGeneratedValues = false, ignore = true) {
                insertFunc(it, type)
            }
        }
    }
}