package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.gen.AnilistLinkData
import me.melijn.gen.database.manager.AbstractAnilistLinkManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class AnilistLinkManager(driverManager: DriverManager) : AbstractAnilistLinkManager(driverManager) {

    suspend fun get(id: Snowflake): AnilistLinkData? {
        return getCachedById(id.value)
    }

}