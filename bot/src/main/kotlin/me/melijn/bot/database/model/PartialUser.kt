package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object PartialUser : Table("partial_user") {

    val userId = ulong("user_id")
    val tag = text("tag")
    val avatarUrl = text("avatar_url").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

    init {
        index(true, userId)
    }
}