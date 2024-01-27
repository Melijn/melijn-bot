package me.melijn.bot.utils

import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.datetime.*
import net.dv8tion.jda.api.entities.ISnowflake
import net.dv8tion.jda.api.utils.TimeFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.toKotlinDuration

/** Time sucks */
object TimeUtil {
    @OptIn(InternalAPI::class)
    fun SimpleDateFormat.parseOrNull(given: String) = try {  parse(given)?.toLocalDateTime() } catch (t: Exception) { null }

    val java.time.LocalDateTime.normalDate
        get() = Date.from(this.atZone(ZoneId.systemDefault()).toInstant())

    fun java.time.Duration.formatElapsed(): String = this.toKotlinDuration().formatElapsed()
    fun Duration.formatElapsed(): String {
        val millis = inWholeMilliseconds % 1000
        val seconds = inWholeSeconds % 60
        val minutes = inWholeMinutes % 60
        val hours = inWholeHours % 24
        val days = inWholeDays % 365

        return when {
            days == 0L && hours == 0L && minutes == 0L && seconds < 3 -> String.format("0:%02d.%03d", seconds, millis)
            days == 0L && hours == 0L -> String.format("%d:%02d", minutes, seconds)
            days == 0L -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d days %d:%02d:%02d", days, hours, minutes, seconds)
        }
    }

    /**
     * **[duration] can have a wider representation of time then [LocalDateTime], in order to prevent overflow we coerce the duration into the usable LocalDateTime space.**
     *
     * @return A new [LocalDateTime] instance which is the sum of [this] and [duration]
     **/
    operator fun LocalDateTime.plus(duration: Duration): LocalDateTime {
        val daysL = duration.inWholeDays.coerceIn(0L until Int.MAX_VALUE).toInt()
        val rest = duration.minus(daysL.days)
        require(rest < 1.days)

        // 0h[+++++++++++++[++|24h ?
        val overflowingTime = this.time.toNanosecondOfDay().nanoseconds + rest
        val timeOverflowed = overflowingTime > 1.days

        // 0h[++++++|------]24h
        val newTimeDuration = if (timeOverflowed) overflowingTime - 1.days else overflowingTime

        val extraDays = if (timeOverflowed) daysL + 1 else daysL

        return LocalDateTime(
            LocalDate.fromEpochDays(this.date.toEpochDays() + extraDays),
            LocalTime.fromMillisecondOfDay(newTimeDuration.inWholeMilliseconds.toInt())
        )
    }

    /**
     * `1s 30ms` (<3 seconds)
     *
     * `1m 30s`, `1h 1m 30s`, `1d, 2h 3m 30s`
     */
    fun Duration.formatElapsedVerbose(): String {
        val millis = inWholeMilliseconds % 1000
        val seconds = inWholeSeconds % 60
        val minutes = inWholeMinutes % 60
        val hours = inWholeHours % 24
        val days = inWholeDays % 365

        return when {
            days == 0L && hours == 0L && minutes == 0L && seconds < 3 ->
                String.format("%ds %dms", seconds, millis)
            days == 0L && hours == 0L ->
                String.format("%dm %ds", minutes, seconds)
            days == 0L ->
                String.format("%dh %dm %ds", hours, minutes, seconds)
            else ->
                String.format("%dd %dh %dm %ds", days, hours, minutes, seconds)
        }
    }

    fun java.time.Duration.formatRelative(): String = this.toKotlinDuration().formatRelative()

    fun Duration.formatRelative(): String {
        val lastPoint = System.currentTimeMillis() - inWholeMilliseconds
        return TimeFormat.RELATIVE.format(Instant.fromEpochMilliseconds(lastPoint))
    }

    fun now(): LocalDateTime = java.time.LocalDateTime.now().toKotlinLocalDateTime()
    fun between(time1: Instant, time2: Instant): Duration {
        return java.time.Duration.between(time1.toJavaInstant(), time2.toJavaInstant()).toKotlinDuration()
    }

    fun TimeFormat.format(createdAt: Instant): String = format(createdAt.toJavaInstant())
    fun TimeFormat.format(flake: ISnowflake): String = format(flake.timeCreated)
}