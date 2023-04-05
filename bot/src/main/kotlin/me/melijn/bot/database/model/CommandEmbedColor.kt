package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable
object CommandEmbedColor : Table("command_embed_color") {

    // guild- or userId
    val entityId = long("entity_id")

    // Color
    val color = integer("color")

    override val primaryKey: PrimaryKey = PrimaryKey(entityId)
}