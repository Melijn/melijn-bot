package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object GlobalXP : Table("global_xp") {

    var userId = long("user_id")
    var xp = long("xp")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}