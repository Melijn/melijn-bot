package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.DriverManager
import me.melijn.gen.PrefixesData
import me.melijn.gen.database.manager.AbstractPrefixesManager

@Inject
class PrefixManager(driverManager: DriverManager) : AbstractPrefixesManager(driverManager) {

    fun getPrefixes(id: Snowflake): List<PrefixesData> {
        return getByIndex0(id.value)
    }

}