package me.melijn.bot.database.manager

import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractCommandEmbedColorManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class CommandEmbedColorManager(driverManager: DriverManager) : AbstractCommandEmbedColorManager(driverManager) {

    /**
     * @param entityId guildId or userId
     */
    suspend fun getColor(entityId: Snowflake): Color? {
        val data = getCachedById(entityId.value)
        return data?.color?.let { Color(it) }
    }
}
