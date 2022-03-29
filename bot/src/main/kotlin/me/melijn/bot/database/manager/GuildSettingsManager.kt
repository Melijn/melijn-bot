package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.gen.GuildSettingsData
import me.melijn.gen.database.manager.AbstractGuildSettingsManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    AbstractGuildSettingsManager(driverManager) {

    suspend fun get(id: Snowflake): GuildSettingsData {
        return getCachedById(id.value) ?: GuildSettingsData(id.value, allowSpacedPrefix = false, allowNsfw = false)
    }
}

