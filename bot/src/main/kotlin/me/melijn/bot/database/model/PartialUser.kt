package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object PartialUser : Table("partial_user") {

    val userId = long("user_id")
    val tag = text("tag")
    val avatarUrl = text("avatar_url").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

    init {
        index(true, userId)
    }
}