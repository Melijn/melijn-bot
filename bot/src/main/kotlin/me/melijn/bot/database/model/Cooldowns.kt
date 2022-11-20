package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table


/** A cooldown in context of (user, commandId)  **/
@CreateTable
@Cacheable
object UserCommandCooldown : Table("user_command_cooldown") {

    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId)


}
@CreateTable
@Cacheable
object UserCommandUsageHistory : Table("user_command_usage_history") {

    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId)

}

/** A cooldown in context of (user) **/
@CreateTable
@Cacheable
object UserCooldown : Table("user_cooldown") {

    val userId = ulong("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
@CreateTable
@Cacheable
object UserUsageHistory : Table("user_usage_history") {

    val userId = ulong("user_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

}

/** A cooldown in context of (messageChannelId) **/
@CreateTable
@Cacheable
object ChannelCooldown : Table("channel_cooldown") {

    val channelId = ulong("user_id")
    val guildId = ulong("guild_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId)
}
@CreateTable
@Cacheable
object ChannelUsageHistory : Table("channel_usage_history") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId)
}

/** A cooldown in context of (guildId) **/
@CreateTable
@Cacheable
object GuildCooldown : Table("guild_cooldown") {

    val guildId = ulong("guild_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)
}

@CreateTable
@Cacheable
object GuildUsageHistory : Table("guild_usage_history") {

    val guildId = ulong("guild_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)
}


/** A cooldown in context of (guildId) **/
@CreateTable
@Cacheable
object GuildUserCooldown : Table("guild_cooldown") {

    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)
}

@CreateTable
@Cacheable
object GuildUserUsageHistory : Table("guild_usage_history") {

    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val usageHistory = binary("usage_history")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)
}



