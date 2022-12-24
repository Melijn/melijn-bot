package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.gen.OsuLinkData
import me.melijn.gen.database.manager.AbstractOsuLinkManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class OsuLinkManager(driverManager: DriverManager) : AbstractOsuLinkManager(driverManager) {

    suspend fun get(id: Snowflake): OsuLinkData {
        return getCachedById(id.value) ?: OsuLinkData(id.value, null, null)
    }

}