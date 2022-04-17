package me.melijn.bot.database.manager

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.melijn.ap.injector.Inject
import me.melijn.gen.PrefixesData
import me.melijn.gen.database.manager.AbstractPrefixesManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class PrefixManager(driverManager: DriverManager) : AbstractPrefixesManager(driverManager) {

    suspend fun getPrefixes(id: Snowflake): List<PrefixesData> {
        return getCachedByIndex0(id.value)
    }

    private suspend fun getCachedByIndex0(id: ULong): List<PrefixesData> {
        val key = "melijn:prefixes:${id}"
        driverManager.getCacheEntry(key, 5)?.run {
            Json.decodeFromString<List<PrefixesData>>(this)
        }
        val cachable = getByIndex0(id)
        val cachableStr = Json.encodeToString(cachable)
        driverManager.setCacheEntry(key, cachableStr, 5)
        return cachable
    }
}