package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractCommandEmbedColorManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.ISnowflake
import java.awt.Color

@Inject
class CommandEmbedColorManager(driverManager: DriverManager) : AbstractCommandEmbedColorManager(driverManager) {

    /**
     * @param entityId guildId or userId
     */
    suspend fun getColor(entityId: ISnowflake): Color? {
        val data = getCachedById(entityId.idLong)
        return data?.color?.let { Color(it) }
    }
}
