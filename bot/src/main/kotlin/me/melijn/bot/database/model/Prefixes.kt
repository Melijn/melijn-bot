package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import org.jetbrains.exposed.sql.Table

@CreateTable
@TableModel(true)
object Prefixes : Table("prefixes") {

    val entityId = long("entity_id")
    val prefix = text("prefix")

    override val primaryKey = PrimaryKey(entityId, prefix)

    init {
        index(false, entityId)
    }
}