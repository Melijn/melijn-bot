package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.AnilistLinkData
import me.melijn.gen.database.manager.AbstractAnilistLinkManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.UserSnowflake

@Inject
class AnilistLinkManager(driverManager: DriverManager) : AbstractAnilistLinkManager(driverManager) {

    suspend fun get(snowflake: UserSnowflake): AnilistLinkData? {
        return getCachedById(snowflake.idLong)
    }

}