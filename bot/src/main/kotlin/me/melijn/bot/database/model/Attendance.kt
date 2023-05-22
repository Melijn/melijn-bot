package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.duration
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
@TableModel(true)
object Attendance: Table("attendance") {

    val attendanceId = long("attendance_id").autoIncrement()

    val guildId = long("guild_id")
    val channelId = long("channel_id")
    val messageId = long("message_id")

    // given to attendees for this attendance event
    val requiredRole = long("required_role_id").nullable()
    val notifyRoleId = long("notify_role_id").nullable()

    // At (nextMoment - closeOffset) we should stop accepting new attendees
    val closeOffset = duration("close_offset").nullable()

    val notifyAttendees = bool("notify_attendees")
    // (nextMoment - notifyOffset) is when attendees are pinged
    val notifyOffset = duration("notify_offset").nullable()

    val topic = text("topic")
    val description = text("description").nullable()

    val repeating = bool("repeating")
    val nextMoment = timestamp("next_moment")

    val state = enumeration("state", AttendanceState::class)

    val nextStateChangeMoment = timestamp("next_state_change_moment")
    // cron format
    val schedule = text("schedule").nullable()

    val zoneId = text("timezone").nullable()

    // time between last moment and starting the next attendance occurrence
    // attendees are cleared when (nextMoment + schedule_timeout) is hit
    val scheduleTimeout = duration("schedule_timeout").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, channelId, messageId)

    init {
        index(true, attendanceId) // name = attendance_key
        index(false, nextStateChangeMoment) // name = attendance_key
        index(false, guildId) // name = guild_key
    }
}

enum class AttendanceState {
    DISABLED,
    LISTENING,
    CLOSED,
    NOTIFIED,
    FINISHED
}

@CreateTable
@TableModel(true)
object Attendees : Table("attendees") {
    val attendanceId = long("attendance_id")
        .references(Attendance.attendanceId, onDelete = ReferenceOption.CASCADE)

    val userId = long("user_id")
    val moment = timestamp("moment")

    override val primaryKey: PrimaryKey = PrimaryKey(attendanceId, userId)

    init {
        index(false, attendanceId) // name = attendence_key
    }
}