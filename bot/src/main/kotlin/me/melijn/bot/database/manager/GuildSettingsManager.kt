package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.annotationprocessors.injector.Inject
import me.melijn.bot.database.DBManager
import me.melijn.bot.database.DriverManager
import me.melijn.bot.database.model.GuildSetting
import me.melijn.bot.database.model.GuildSettings

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    DBManager<ULong, GuildSettings, GuildSetting>(driverManager, GuildSettings, GuildSetting) {

    fun get(id: Snowflake): GuildSetting? {
        return get(id.value)
    }

    fun set() {

    }
}


