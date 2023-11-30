package me.melijn.bot.services

import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.RunnableTask
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

abstract class Service(
    val name: String,
    private val delay: Duration,
    private val repeat: Duration,
    val autoStart: Boolean = true
) {

    private val scheduledExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(serviceThreadFactory(name))
    private var future: ScheduledFuture<*>? = null
    val logger by Log

    companion object {
        private val serviceThreadFactory = { name: String ->
            { r: Runnable ->
                Thread.ofVirtual().name("[$name-Service]").unstarted(r)
            }
        }
    }

    abstract val service: RunnableTask

    fun start() {
        if (future == null) {
            future = scheduledExecutor.scheduleAtFixedRate(
                service,
                delay.inWholeMilliseconds,
                repeat.inWholeMilliseconds,
                TimeUnit.MILLISECONDS
            )
            logger.info { "Started $name-Service" }
        } else {
            logger.warn { "Tried starting $name-Service but this service is already running" }
        }
    }

    open fun stop() {
        future?.cancel(false)
        logger.info { "Stopped $name-Service" }
    }
}
