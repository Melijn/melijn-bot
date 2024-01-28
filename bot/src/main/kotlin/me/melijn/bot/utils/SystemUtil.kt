package me.melijn.bot.utils

import java.io.File
import java.util.regex.Pattern

object SystemUtil {

    val isUnix: Boolean = os == OS.UNIX

    // 11105353.49 239988480.98
    val linuxUptimePattern: Pattern = Pattern.compile("([0-9]+)(?:\\.[0-9]+)? [0-9]+(?:\\.[0-9]+)?")

    val linuxMemTotalPattern: Pattern = Pattern.compile("MemTotal:\\s+([0-9]+) kB")
    val linuxMemAvailPattern: Pattern = Pattern.compile("MemAvailable:\\s+([0-9]+) kB")

    val os: OS
        get() {
            val s = System.getProperty("os.name").lowercase()
            return when {
                s.contains("mac") || s.contains("nix") || s.contains("nux") || s.contains("aix") -> OS.UNIX
                else -> OS.OTHER
            }
        }

    enum class OS {
        UNIX, OTHER
    }

    fun getUnixUptime(): Long {
        val uptimeStr = File("/proc/uptime").readText()
        val matcher = linuxUptimePattern.matcher(uptimeStr)

        if (!matcher.find()) return -1 // Extract ints out of groups
        return matcher.group(1).toLong()
    }

    fun getTotalMBUnixRam(): Long = File("/proc/meminfo").readLines().firstNotNullOf { line ->
        val matcher = linuxMemTotalPattern.matcher(line)

        if (!matcher.find()) return@firstNotNullOf null
        val group = matcher.group(1)
        group.toLong() / 1024
    }


    fun getAvailableMBUnixRam(): Long  = File("/proc/meminfo").readLines().firstNotNullOf { line ->
        val matcher = linuxMemAvailPattern.matcher(line)

        if (!matcher.find()) return@firstNotNullOf null
        val group = matcher.group(1)
        group.toLong() / 1024
    }

}