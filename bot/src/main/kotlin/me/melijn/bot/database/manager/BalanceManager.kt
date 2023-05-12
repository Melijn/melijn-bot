package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.UserBalance
import me.melijn.gen.UserBalanceData
import me.melijn.gen.database.manager.AbstractUserBalanceManager
import me.melijn.kordkommons.database.DriverManager
import me.melijn.kordkommons.database.insertOrUpdate
import net.dv8tion.jda.api.entities.ISnowflake
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

@Inject
class BalanceManager(driverManager: DriverManager) : AbstractUserBalanceManager(driverManager) {

    suspend fun get(flake: ISnowflake): UserBalanceData {
        val userId = flake.idLong
        return getById(userId) ?: UserBalanceData(userId, 0)
    }

    fun add(id: ISnowflake, amount: Long) {
        update(id, amount) { it + amount }
    }

    fun min(id: ISnowflake, amount: Long) {
        update(id, amount) { it - amount }
    }

    private fun update(flake: ISnowflake, amount: Long, exp: (Column<Long>) -> Expression<Long>) {
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
}