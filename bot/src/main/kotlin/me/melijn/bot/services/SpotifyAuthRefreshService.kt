package me.melijn.bot.services

import me.melijn.bot.model.WebManager
import me.melijn.bot.utils.threading.RunnableTask
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.minutes


class SpotifyAuthRefreshService : Service("spotify", 0.minutes, 25.minutes, true) {

    val webManager by inject<WebManager>(WebManager::class.java)
    override val service: RunnableTask = RunnableTask {
        webManager.spotifyApi?.updateSpotifyCredentials()
        println("updated")
    }
}