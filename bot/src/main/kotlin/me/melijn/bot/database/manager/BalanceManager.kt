package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.UserBalance
import me.melijn.gen.UserBalanceData
import me.melijn.gen.database.manager.AbstractUserBalanceManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

@Inject
class BalanceManager(driverManager: DriverManager) : AbstractUserBalanceManager(driverManager) {

    suspend fun get(id: Snowflake): UserBalanceData {
        return getCachedById(id.value) ?: UserBalanceData(id.value, 0)
    }

    fun add(id: Snowflake, amount: Long) {
        update(id, amount) { it + amount }
    }

    fun min(id: Snowflake, amount: Long) {
        update(id, amount) { it - amount }
    }

    private fun update(id: Snowflake, amount: Long, exp: (Column<Long>) -> Expression<Long>) {
        scopedTransaction {
            UserBalance.insertOrUpdate({
                it[this.userId] = id.value
                it[this.balance] = amount
            }, {
                it.update(balance, exp(balance))
            })
        }
        driverManager.removeCacheEntry(id.value.toString()) // drop cache due to possible race conditions
    }
}