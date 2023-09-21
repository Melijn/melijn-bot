package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.GuildSettingsData
import me.melijn.gen.database.manager.AbstractGuildSettingsManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.ISnowflake

@Inject
class GuildSettingsManager(override val driverManager: DriverManager) :
    AbstractGuildSettingsManager(driverManager) {

    suspend fun get(id: ISnowflake): GuildSettingsData {
        return getById(id.idLong) ?: GuildSettingsData(
            id.idLong,
            allowSpacedPrefix = false,
            allowNsfw = false,
            allowVoiceTracking = false,
            allowInviteTracking = false,
            enableNameNormalization = false,
            enableLeveling = false
        )
    }
}

