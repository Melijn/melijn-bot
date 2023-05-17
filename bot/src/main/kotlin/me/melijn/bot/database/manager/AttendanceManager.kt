package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Attendance
import me.melijn.gen.database.manager.AbstractAttendanceManager
import me.melijn.gen.database.manager.AbstractAttendeesManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

@Inject
class AttendanceManager(override val driverManager: DriverManager) : AbstractAttendanceManager(driverManager) {

    fun delete(attendanceId: Long, guildId: Long): Boolean {
        return scopedTransaction {
            Attendance.deleteWhere {
                (Attendance.attendanceId eq attendanceId) and (Attendance.guildId eq guildId)
            } == 1
        }
    }

}

@Inject
class AttendeesManager(override val driverManager: DriverManager) : AbstractAttendeesManager(driverManager) {

}