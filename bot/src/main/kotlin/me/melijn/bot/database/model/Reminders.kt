package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
@TableModel(true)
object Reminders : Table("reminders") {

    val userId = long("user_id")
    val moment = timestamp("moment")
    val reminderText = text("reminder_text")

    override val primaryKey = PrimaryKey(userId, moment)

    init {
        index(false, userId, moment)
        index("moment_index", false, moment)
        index("user_index", false, userId)
    }
}