package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object GlobalXP : Table("global_xp") {

    var userId = long("user_id")
    var xp = long("xp")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

    init {
        index(true, userId)
    }
}