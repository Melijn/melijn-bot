package me.melijn.bot.database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object CommandEmbedColor : Table("command_embed_color") {

    // guil- or user Id
    val entityId = ulong("entity_id").nullable()

    // can be disabled or enabled
    val color = integer("color")

    override val primaryKey: PrimaryKey = PrimaryKey(entityId)
}