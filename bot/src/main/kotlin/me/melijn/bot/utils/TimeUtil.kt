package me.melijn.bot.utils

import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinLocalDateTime
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

object TimeUtil {
    fun java.time.Duration.formatElapsed(): String = this.toKotlinDuration().formatElapsed()
    fun Duration.formatElapsed(): String {
        val millis = inWholeMilliseconds % 1000
        val seconds = inWholeSeconds % 60
        val minutes = inWholeMinutes % 60
        val hours = inWholeHours % 24
        val days = inWholeDays % 356

        return when {
            days == 0L && hours == 0L && minutes == 0L && seconds < 3 -> String.format("0:%02d.%03d", seconds, millis)
            days == 0L && hours == 0L -> String.format("%d:%02d", minutes, seconds)
            days == 0L -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d days %d:%02d:%02d", days, hours, minutes, seconds)
        }
    }
    fun java.time.Duration.formatRelative(): String = this.toKotlinDuration().formatRelative()
    fun Duration.formatRelative(): String {
        val lastPoint = System.currentTimeMillis() - inWholeMilliseconds
        return Instant.fromEpochMilliseconds(lastPoint).toMessageFormat(DiscordTimestampStyle.RelativeTime)
    }

    fun now(): LocalDateTime = java.time.LocalDateTime.now().toKotlinLocalDateTime()
    fun between(time1: Instant, time2: Instant): Duration {
        return java.time.Duration.between(time1.toJavaInstant(), time2.toJavaInstant()).toKotlinDuration()
    }
}