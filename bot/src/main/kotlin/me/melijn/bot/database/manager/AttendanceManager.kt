package me.melijn.bot.database.manager

import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Attendance
import me.melijn.gen.AttendanceData
import me.melijn.gen.database.manager.AbstractAttendanceManager
import me.melijn.gen.database.manager.AbstractAttendeesManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import kotlin.time.Duration

@Inject
class AttendanceManager(override val driverManager: DriverManager) : AbstractAttendanceManager(driverManager) {
    fun genstore(
        topic: String,
        guildId: Long,
        channelId: Long,
        messageId: Long,
        notifyAttendees: Boolean,
        repeating: Boolean,
        nextMoment: Instant,
        schedule: String? = null,
        description: String? = null,
        roleId: Long? = null,
        closeOffset: Duration? = null,
        notifyOffset: Duration? = null,
        scheduleTimeout: Duration? = null,
    ): AttendanceData {
        return scopedTransaction {
            val resultedValues = Attendance.insert {
                it[Attendance.guildId] = guildId
                it[Attendance.channelId] = channelId
                it[Attendance.messageId] = messageId
                it[Attendance.notifyAttendees] = notifyAttendees
                it[Attendance.repeating] = repeating
                it[Attendance.topic] = topic
                it[Attendance.nextMoment] = nextMoment
                it[Attendance.schedule] = schedule
                it[Attendance.description] = description
                it[Attendance.roleId] = roleId
                it[Attendance.closeOffset] = closeOffset
                it[Attendance.notifyOffset] = notifyOffset
                it[Attendance.scheduleTimeout] = scheduleTimeout
            }.resultedValues
            val resRow1 = resultedValues?.get(0)!!
            val resRow = AttendanceData.fromResRow(resRow1)
            resRow
        }
    }

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