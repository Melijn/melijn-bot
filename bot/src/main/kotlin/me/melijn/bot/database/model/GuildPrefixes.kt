package me.melijn.bot.database.model

import me.melijn.annotationprocessors.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@CreateTable
@Cacheable<Prefixes>
object Prefixes : Table("prefixes") {

    @OptIn(ExperimentalUnsignedTypes::class)
    val entityId = ulong("entity_id")
    val prefix = text("prefix")

    override val primaryKey = PrimaryKey(entityId, prefix)

    init {
        index(false, entityId)
    }
}