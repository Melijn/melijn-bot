package me.melijn.bot.web.server

import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import me.melijn.bot.model.PodInfo
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import me.melijn.gen.Settings

object RestServer {

    val settings by inject<Settings>()
    private val logger by Log

    private val server: NettyApplicationEngine = embeddedServer(Netty, settings.httpServer.port, configure = {
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

    init {
        server.start()
        logger.info { "HttpServer on: http://localhost:${settings.httpServer.port}" }
    }
}