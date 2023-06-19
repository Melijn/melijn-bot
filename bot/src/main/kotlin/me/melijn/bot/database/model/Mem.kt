package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object Mem : Table() {
    val guildId = long("guild_id")
    val name = text("name")
    val url = text("url")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, name)

    init {
        index("guild_idx", false, guildId)
    }
}