package me.melijn.bot.database.manager

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kord.common.entity.Snowflake
import me.melijn.annotationprocessors.injector.Inject
import me.melijn.bot.GuildSettingsData
import me.melijn.bot.database.DBManager
import me.melijn.bot.database.DriverManager
import me.melijn.bot.database.model.GuildSetting
import me.melijn.bot.database.model.GuildSettings
import me.melijn.bot.toCache

val objectManager = jacksonObjectMapper()

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    DBManager<ULong, GuildSettings, GuildSetting>(driverManager, GuildSettings, GuildSetting) {

    suspend fun get(id: Snowflake): GuildSettingsData? {
        return getCached(id.value)
    }

    private suspend fun getCached(value: ULong): GuildSettingsData? {
        val uwaa = "melijn:${this.idTable.tableName}:$value"
        val sex = driverManager.getCacheEntry(uwaa)
        if (sex != null) {
            return objectManager.readValue<GuildSettingsData>(sex)
        }
        val cached = get(value)?.toCache() ?: return null
        driverManager.setCacheEntry(uwaa, cached.toString(), 5)
        return cached
    }

    fun set() {

    }
}


