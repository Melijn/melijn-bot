package me.melijn.bot.database.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*

abstract class TrackJoinTable(val name: String) : Table(name) {
    abstract val trackId: Column<UUID>
}
