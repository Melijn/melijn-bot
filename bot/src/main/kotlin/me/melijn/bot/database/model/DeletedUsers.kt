package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object DeletedUsers : Table("deleted_users") {

    var userId = long("user_id")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}

@CreateTable
@TableModel(true)
object NoDmsUsers : Table("no_dms_users") {

    var userId = long("user_id")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}

@CreateTable
@TableModel(true)
object MissingMembers : Table("missing_members") {

    var guildId = long("guild_id")
    var userId = long("user_id")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}