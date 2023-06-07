package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Attendance
import me.melijn.bot.database.model.AttendanceState
import me.melijn.gen.AttendanceData
import me.melijn.gen.database.manager.AbstractAttendanceManager
import me.melijn.gen.database.manager.AbstractAttendeesManager
import me.melijn.kordkommons.database.DriverManager
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

@Inject
class AttendanceManager(override val driverManager: DriverManager) : AbstractAttendanceManager(driverManager) {

    init {
        val nextMomentColName = Attendance.nextMoment.name
        val closeOffsetColName = Attendance.closeOffset.name
        val notifyOffsetColName = Attendance.notifyOffset.name
        val reopenOffsetColName = Attendance.scheduleTimeout.name
        @Language("postgresql") val indices =
            """
        CREATE INDEX IF NOT EXISTS ${nextMomentColName}_min_${closeOffsetColName}_idx ON Attendance (($nextMomentColName - (($closeOffsetColName/10e9) * INTERVAL '1 second')));
        CREATE INDEX IF NOT EXISTS ${nextMomentColName}_min_${notifyOffsetColName}_idx ON Attendance (($nextMomentColName - (($notifyOffsetColName/10e9) * INTERVAL '1 second')));
        CREATE INDEX IF NOT EXISTS ${nextMomentColName}_plus_${reopenOffsetColName}_idx ON Attendance (($nextMomentColName - (($reopenOffsetColName/10e9) * INTERVAL '1 second')));
        """.trimIndent()
        driverManager.executeUpdate(indices)
    }

    suspend fun delete(attendanceId: Long, guildId: Long): Boolean {
        return scopedTransaction {
            Attendance.deleteWhere {
                (Attendance.attendanceId eq attendanceId) and (Attendance.guildId eq guildId)
            } == 1
        }
    }

    suspend fun getNextChangingEntry(): AttendanceData? {
        return scopedTransaction {
            Attendance.select {
                Attendance.state neq AttendanceState.DISABLED
            }.orderBy(Attendance.nextStateChangeMoment, SortOrder.ASC)
                .limit(1)
                .iterator()
                .asSequence()
                .firstOrNull()
                ?.let { AttendanceData.fromResRow(it) }
        }
    }
}

@Inject
class AttendeesManager(override val driverManager: DriverManager) : AbstractAttendeesManager(driverManager) {

}