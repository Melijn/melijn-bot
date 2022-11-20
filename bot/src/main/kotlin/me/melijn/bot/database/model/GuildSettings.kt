package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.dao.id.IdTable

@CreateTable
@Cacheable
object GuildSettings : IdTable<ULong>("guild_settings") {

    @OptIn(ExperimentalUnsignedTypes::class)
    override var id = ulong("guild_id").entityId()

    var allowSpacedPrefix = bool("allow_spaced_prefix").default(false)
    var allowNsfw = bool("allow_nsfw").default(false)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}