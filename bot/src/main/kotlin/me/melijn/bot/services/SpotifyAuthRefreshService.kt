package me.melijn.bot.services

import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.web.api.WebManager
import me.melijn.kordkommons.async.RunnableTask
import kotlin.time.Duration.Companion.minutes

@Inject(true)
class SpotifyAuthRefreshService : Service("spotify", 0.minutes, 25.minutes) {

    private val webManager by inject<WebManager>()
    override val service: RunnableTask = RunnableTask {
        val spotifyApi = webManager.spotifyApi ?: return@RunnableTask
        spotifyApi.updateSpotifyCredentials()
        val token = spotifyApi.getToken()
        logger.info { "Updated spotify credentials." }
    }
}