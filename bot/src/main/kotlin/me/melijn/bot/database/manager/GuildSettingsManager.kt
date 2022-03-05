package me.melijn.bot.database.manager

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kord.common.entity.Snowflake
import me.melijn.annotationprocessors.injector.Inject
import me.melijn.bot.database.DriverManager
import me.melijn.gen.GuildSettingsData
import me.melijn.gen.database.manager.AbstractGuildSettingsManager

val objectManager = jacksonObjectMapper()

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    AbstractGuildSettingsManager(driverManager) {

    suspend fun get(id: Snowflake): GuildSettingsData? {
        return getCached(id.value)
    }

    private suspend fun getCached(id: ULong): GuildSettingsData? {
        val key = "melijn:${this.table.tableName}:$id"
        driverManager.getCacheEntry(key)?.run {
            return objectManager.readValue<GuildSettingsData>(this)
        }
        return null
//        val cachable = get(id)?.toCache() ?: return null
//        driverManager.setCacheEntry(key, cachable.toString(), 5)
//        return cachable
    }

    fun set(value: GuildSettingsData) {
        val key = "melijn:${this.table.tableName}:${value.id}"

    }
}
