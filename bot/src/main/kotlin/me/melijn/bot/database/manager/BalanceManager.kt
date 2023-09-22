package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.LeaderboardData
import me.melijn.bot.commands.bigNumberFormatter
import me.melijn.bot.database.model.DeletedUsers
import me.melijn.bot.database.model.MissingMembers
import me.melijn.bot.database.model.UserBalance
import me.melijn.bot.utils.StringsUtil.format
import me.melijn.gen.UserBalanceData
import me.melijn.gen.database.manager.AbstractUserBalanceManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import net.dv8tion.jda.api.entities.ISnowflake
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Inject
class BalanceManager(driverManager: DriverManager) : AbstractUserBalanceManager(driverManager) {

    suspend fun get(flake: ISnowflake): UserBalanceData {
        val userId = flake.idLong
        return getById(userId) ?: UserBalanceData(userId, 0)
    }

    suspend fun add(id: ISnowflake, amount: Long) {
        update(id, amount) { it + amount }
    }

    suspend fun min(id: ISnowflake, amount: Long) {
        update(id, amount) { it - amount }
    }

    private suspend fun update(flake: ISnowflake, amount: Long, exp: (Column<Long>) -> Expression<Long>) {
        scopedTransaction {
            UserBalance.insertOrUpdate({
                it[this.userId] = flake.idLong
                it[this.balance] = amount
            }, {
                it.update(balance, exp(balance))
            })
        }
        driverManager.removeCacheEntry("$classKeyPrefix:${flake.id}") // drop cache due to possible race conditions
    }

    suspend fun getTop(count: Int, offset: Long): List<AugmentedBalanceData> {
        return scopedTransaction {
            UserBalance.join(DeletedUsers, JoinType.LEFT) {
                UserBalance.userId.eq(DeletedUsers.userId)
            }.selectAll()
                .orderBy(UserBalance.balance, SortOrder.DESC)
                .limit(count, offset)
                .mapIndexed { index, rr ->
                    AugmentedBalanceData(
                        UserBalanceData.fromResRow(rr),
                        offset + index,
                        rr.getOrNull(MissingMembers.userId) != null
                    )
                }
        }
    }

    suspend fun rowCount(): Long = scopedTransaction {
        UserBalance.selectAll().count()
    }

    suspend fun getPosition(userId: Long): AugmentedBalanceData? = suspendCoroutine { continuation ->
        @Language("postgresql")
        val query1 =
            """SELECT subq.user_id, subq.balance, position, subq.muser_id
                  |FROM (
                  |    SELECT user_balance.user_id, user_balance.balance, ROW_NUMBER() over (order by user_balance.balance desc) as position, 
                  |        deleted_users.user_id as muser_id
                  |    FROM user_balance LEFT JOIN deleted_users ON user_balance.user_id = deleted_users.user_id
                  |    ORDER BY user_balance.balance DESC) subq 
                  |WHERE subq.user_id = ?
                  |""".trimMargin()
        driverManager.executeQuery(query1, { rs ->
            if (rs.next()) {
                val entry = UserBalanceData(
                    rs.getLong(1),
                    rs.getLong(2)
                )
                val rowNumber = rs.getLong(3)

                // https://stackoverflow.com/questions/2920364/checking-for-a-null-int-value-from-a-java-resultset
                // Makes rs work on missing_users.user_id and consider it's nullability below
                rs.getLong(4)
                val augmentedGuildXPData = AugmentedBalanceData(entry, rowNumber, !rs.wasNull())
                continuation.resume(augmentedGuildXPData)
            } else continuation.resume(null)
        }, userId)
    }
}

data class AugmentedBalanceData(
    val balanceData: UserBalanceData,
    override val position: Long,
    override val missing: Boolean
) : LeaderboardData {
    override val userId = balanceData.userId
    override val dataList: List<String> = listOf(balanceData.balance.format(bigNumberFormatter))
}

