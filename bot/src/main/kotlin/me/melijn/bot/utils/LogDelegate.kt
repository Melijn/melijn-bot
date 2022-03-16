package me.melijn.bot.utils

import mu.KLogger
import mu.KotlinLogging
import org.slf4j.LoggerFactory

object Log {
    operator fun getValue(thisRef: Any?, prop: Any): KLogger {
        return KotlinLogging.logger(LoggerFactory.getLogger(thisRef!!::class.java)!!)
    }
}