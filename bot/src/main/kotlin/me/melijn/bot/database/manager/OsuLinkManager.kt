package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.OsuLinkData
import me.melijn.gen.database.manager.AbstractOsuLinkManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.UserSnowflake

@Inject
class OsuLinkManager(driverManager: DriverManager) : AbstractOsuLinkManager(driverManager) {

    suspend fun get(id: UserSnowflake): OsuLinkData {
        return getCachedById(id.idLong) ?: OsuLinkData(id.idLong, null, null)
    }

}