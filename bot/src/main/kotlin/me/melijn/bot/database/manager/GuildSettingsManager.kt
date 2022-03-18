package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.annotationprocessors.injector.Inject
import me.melijn.bot.database.DriverManager
import me.melijn.gen.GuildSettingsData
import me.melijn.gen.database.manager.AbstractGuildSettingsManager

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    AbstractGuildSettingsManager(driverManager) {

    suspend fun get(id: Snowflake): GuildSettingsData {
        return getCachedById(id.value) ?: GuildSettingsData(id.value, allowSpacedPrefix = false, allowNsfw = false)
    }
}

