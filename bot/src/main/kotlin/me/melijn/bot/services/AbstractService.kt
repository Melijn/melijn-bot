package me.melijn.bot.services

import me.melijn.bot.utils.threading.RunnableTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

abstract class Service(
    val name: String,
    val delay: Duration,
    val repeat: Duration,
    val autoStart: Boolean = true
) {

    companion object {
        private val serviceThreadFactory = { name: String ->
            { r: Runnable ->
                Thread(r, "[$name-Service]")
            }
        }
    }

    private val scheduledExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(serviceThreadFactory(name))
    private lateinit var future: ScheduledFuture<*>
    val logger: Logger = LoggerFactory.getLogger(name)

    abstract val service: RunnableTask

    open fun start() {
        future = scheduledExecutor.scheduleAtFixedRate(service, delay.inWholeMilliseconds, repeat.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        logger.info("Started $name-Service")
    }

    open fun stop() {
        future.cancel(false)
        logger.info("Stopped $name-Service")
    }
}
