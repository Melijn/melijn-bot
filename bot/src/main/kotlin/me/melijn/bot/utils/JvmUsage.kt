package me.melijn.bot.utils

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory

data class JvmUsage(val totalMem: Long, val usedMem: Long, val totalJVMMem: Long, val usedJVMMem: Long) {

    companion object {
        fun current(bean: OperatingSystemMXBean): JvmUsage {
            val totalMem: Long
            val usedMem: Long
            if (SystemUtil.isUnix) {
                totalMem = SystemUtil.getTotalMBUnixRam()
                val available = SystemUtil.getAvailableMBUnixRam()
                usedMem = totalMem - available
            } else {
                totalMem = bean.totalMemorySize shr 20
                usedMem = totalMem - (bean.freeSwapSpaceSize shr 20)
            }

            val totalJVMMem = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max shr 20
            val usedJVMMem = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used shr 20

            return JvmUsage(totalMem, usedMem, totalJVMMem, usedJVMMem)
        }
    }
}
