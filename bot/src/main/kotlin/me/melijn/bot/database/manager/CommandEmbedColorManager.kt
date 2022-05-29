package me.melijn.bot.database.manager

import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import me.melijn.gen.database.manager.AbstractCommandEmbedColorManager
import me.melijn.kordkommons.database.DriverManager

class CommandEmbedColorManager(driverManager: DriverManager) : AbstractCommandEmbedColorManager(driverManager) {

    /**
     * @param guildId guildId ?
     * @param scopeId channelId or null for all channels
     * @param entityId roleId or userId
     * @param commandId can be command id, command category, * for all
     */
    suspend fun getColor(guildId: Snowflake, scopeId: Snowflake?, entityId: Snowflake?, commandId: String): Color? {
        val data = getCachedById(guildId.value, scopeId?.value, entityId?.value, commandId)
        return data?.color?.let { Color(it) }
    }

}
