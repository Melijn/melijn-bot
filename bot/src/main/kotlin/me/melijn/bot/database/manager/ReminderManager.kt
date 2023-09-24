package me.melijn.bot.database.manager

import kotlinx.datetime.toKotlinInstant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.model.Reminders
import me.melijn.gen.RemindersData
import me.melijn.gen.database.manager.AbstractRemindersManager
import me.melijn.kordkommons.database.DriverManager
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

@Inject
class ReminderManager(override val driverManager: DriverManager) : AbstractRemindersManager(driverManager) {

    suspend fun getNextUpcomingReminder(): RemindersData? = scopedTransaction {
        Reminders.selectAll()
            .orderBy(Reminders.moment, SortOrder.ASC)
            .limit(1, 0)
            .firstOrNull()
            ?.let { RemindersData.fromResRow(it) }
    }

    suspend fun getPassedReminders(): List<RemindersData> = scopedTransaction {
        Reminders.select { Reminders.moment.lessEq(Instant.now().toKotlinInstant()) }
            .orderBy(Reminders.moment, SortOrder.ASC)
            .map { RemindersData.fromResRow(it) }
    }

    suspend fun bulkDelete(items: Collection<RemindersData>) = scopedTransaction {
        for (item in items) {
            delete(item)
        }
    }

}