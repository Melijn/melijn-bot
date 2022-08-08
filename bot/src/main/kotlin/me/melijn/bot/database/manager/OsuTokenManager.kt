package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.kordkommons.database.DriverManager
import java.util.concurrent.TimeUnit

private const val KEY = "melijn:osu_token"

@Inject
class OsuTokenManager(private val driverManager: DriverManager) {

    fun store(token: String, ttl: Int) = driverManager.setCacheEntry(KEY, token, ttl, ttlUnit = TimeUnit.SECONDS)

    suspend fun get(): String? = driverManager.getCacheEntry(KEY)

}