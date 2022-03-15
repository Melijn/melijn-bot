package me.melijn.bot.database.model

import me.melijn.annotationprocessors.createtable.CreateTable
import org.jetbrains.exposed.sql.Table


@CreateTable
@Cacheable<GuildUserSettings>
object GuildUserSettings : Table("guild_user_settings") {

    @OptIn(ExperimentalUnsignedTypes::class)
    var guildId = ulong("guild_id")
    @OptIn(ExperimentalUnsignedTypes::class)
    var userId = ulong("user_id")
    var prefixes = text("prefixes").default("")
    var allowNsfw = bool("allow_nsfw").default(false)

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}