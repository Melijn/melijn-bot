package me.melijn.bot.database.manager

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Attendance
import me.melijn.bot.utils.ExposedUtil.CustomExpression
import me.melijn.gen.AttendanceData
import me.melijn.gen.database.manager.AbstractAttendanceManager
import me.melijn.gen.database.manager.AbstractAttendeesManager
import me.melijn.kordkommons.database.DriverManager
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import kotlin.time.Duration

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

    fun delete(attendanceId: Long, guildId: Long): Boolean {
        return scopedTransaction {
            Attendance.deleteWhere {
                (Attendance.attendanceId eq attendanceId) and (Attendance.guildId eq guildId)
            } == 1
        }
    }

    /** @return entries where (nextMoment - closeOffset) < now() + [upto] **/
    fun getEntriesAboutToClose(upto: Duration): List<AttendanceData> {
        return getEntriesAboutToDoSomething(upto, Attendance.closeOffset) { column, expression ->
            column.minus(expression)
        }
    }
    /** @return entries where (nextMoment - notifyOffset) < now() + [upto] **/
    fun getEntriesAboutToNotify(upto: Duration): List<AttendanceData> {
        return getEntriesAboutToDoSomething(upto, Attendance.notifyOffset) { column, expression ->
            column.minus(expression)
        }
    }
    /** @return entries where (nextMoment + scheduleTimeout) < now() + [upto] **/
    fun getEntriesAboutToReopen(upto: Duration): List<AttendanceData> {
        return getEntriesAboutToDoSomething(upto, Attendance.scheduleTimeout) { column, expression ->
            column.plus(expression)
        }
    }

    private inline fun getEntriesAboutToDoSomething(
        includeRangeFromNow: Duration,
        offsetColumn: Column<Duration?>,
        crossinline operator: (Column<Instant>, Expression<Instant>) -> CustomOperator<Instant>
    ): List<AttendanceData> {
        val now = Clock.System.now()
        val threshold = now + includeRangeFromNow
        return scopedTransaction {
            // makes exposed think this is an instant, so it can use the minus operator
            val offsetAsInstant = CustomExpression<Instant>("((${offsetColumn.name} / 1e+9) * INTERVAL '1 second')")
            Attendance.select {
                (offsetColumn neq null) and ((operator(Attendance.nextMoment, offsetAsInstant)) lessEq threshold)
            }.map {
                AttendanceData.fromResRow(it)
            }
        }
    }

}

@Inject
class AttendeesManager(override val driverManager: DriverManager) : AbstractAttendeesManager(driverManager) {

}