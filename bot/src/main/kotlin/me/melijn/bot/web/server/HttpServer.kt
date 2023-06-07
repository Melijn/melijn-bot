package me.melijn.bot.web.server

import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.schlaubi.lavakord.audio.Link
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.melijn.bot.database.manager.BotRestartTrackEntryManager
import me.melijn.bot.database.manager.BotRestartTrackQueueManager
import me.melijn.bot.model.PodInfo
import me.melijn.bot.music.MusicManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import me.melijn.gen.BotRestartTrackQueueData
import me.melijn.gen.Settings
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.TimeUnit

@Suppress("ExtractKtorModule")
object HttpServer {

    val settings by inject<Settings>()
    private val logger by Log

    /**
     * A public http rest api for fetching pod specific info.
     * 
     * Offers:
     * - pod information
     */
    private val apiServer: NettyApplicationEngine = embeddedServer(Netty, settings.httpServer.port, configure = {
        this.runningLimit = settings.httpServer.runningLimit
        this.requestQueueLimit = settings.httpServer.requestQueueLimit
    }) {
        routing {
            get("/") {
                this.context.respond("fiwh")
            }

            get("/podinfo") {
                this.context.respond(PodInfo.json())
            }
        }
    }

    /**
     * A management httpServer.
     * 
     * Offers:
     * - graceful shutdown
     * - health check
     */
    private val probeServer: NettyApplicationEngine = embeddedServer(Netty, settings.probeServer.port, configure = {
        this.runningLimit = settings.probeServer.runningLimit
        this.requestQueueLimit = settings.probeServer.requestQueueLimit
    }) {
        routing {
            get("/shutdown") {
                logger.info { "ProbeServer: shutdown received!" }
                val kord = getKoin().getOrNull<ShardManager>()

                val botRestartTrackQueueManager = getKoin().getOrNull<BotRestartTrackQueueManager>()
                val botRestartTrackEntryManager = getKoin().getOrNull<BotRestartTrackEntryManager>()

                if (botRestartTrackEntryManager != null && botRestartTrackQueueManager != null) {
                    MusicManager.guildMusicPlayers.forEach { (guildId, trackManager) ->
                        if (trackManager.link.state in arrayOf(Link.State.CONNECTED, Link.State.CONNECTING)
                            && (trackManager.playingTrack != null || trackManager.queue.size != 0)
                        ) {
                            // save stuff
                            val playingTrack = trackManager.playingTrack
                            val queue = trackManager.queue
                            val vc = trackManager.link.lastChannelId ?: throw IllegalStateException(":thonk:")
                            botRestartTrackQueueManager.store(
                                BotRestartTrackQueueData(
                                    guildId, vc.toLong(), trackManager.player.position, trackManager.player.paused,
                                    trackManager.looped, trackManager.loopedQueue
                                )
                            )
                            if (playingTrack != null) {
                                botRestartTrackEntryManager.newTrack(guildId, 0, playingTrack)
                            }
                            queue.indexedForEach { i, track ->
                                botRestartTrackEntryManager.newTrack(guildId, i + 1, track)
                            }

                            trackManager.link.destroy()
                        }
                    }
                }

                kord?.shutdown()
                logger.info { "ProbeServer: shutdown complete!" }
                context.respond(HttpStatusCode.OK, "Shutdown complete!")
                stopAll()
            }

            get("/ready") {
                val kord = getKoin().getOrNull<ShardManager>()
                if (kord == null) {
                    logger.info { "ProbeServer: ready check - not ready!" }
                    context.respond(HttpStatusCode(500, "not ready"), "not ready")
                } else {
                    context.respond(HttpStatusCode.OK, "ready")
                }
            }
        }
    }

    private fun stopAll() {
        probeServer.stop(0, 2, TimeUnit.SECONDS)
        apiServer.stop(0, 2, TimeUnit.SECONDS)
    }

    fun startHttpServer() {
        apiServer.start()
        logger.info { "HttpServer on: http://localhost:${settings.httpServer.port}" }
    }

    fun startProbeServer() {
        probeServer.start()
        logger.info { "ProbeServer on: http://localhost:${settings.probeServer.port}" }
    }
}