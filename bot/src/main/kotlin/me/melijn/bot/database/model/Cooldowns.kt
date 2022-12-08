package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** This does only hold normal command uses, not ratelimit hits or cooldown hits **/
@CreateTable
@Cacheable
object UsageHistory : Table("usage_history") {

    val guildId = ulong("guild_id").nullable()
    val channelId = ulong("channel_id")
    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")

    override val primaryKey: PrimaryKey= PrimaryKey(guildId, channelId, userId, moment)

    init {
        index(false, guildId) // name = guild_key
        index(false, guildId, commandId) // name = guild_command_key
        index(false, channelId) // name = channel_key
        index(false, channelId, commandId) // name = channel_command_key
        index(false, userId) // name = user_key
        index(false, userId, commandId) // name = user_command_key
        index(false, guildId, userId) // name = guild_user_key
        index(false, guildId, userId, commandId) // name = guild_user_command_key
        index(false, channelId, userId) // name = channel_user_key
        index(false, channelId, userId, commandId) // name = channel_user_command_key
        index(false, moment) // name = moment_key
    }
}

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
object UserCommandUseLimitHistory : Table("user_command_use_limit_history") {

    val userId = ulong("user_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId, moment, type)

    init {
        index("user_command_key", false, userId, commandId)
        index(false, userId) // name = gdpr_key
    }
}

enum class UseLimitHitType {
    COOLDOWN,
    RATELIMIT
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
object UserUseLimitHistory : Table("user_use_limit_history") {

    val userId = ulong("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, moment, type)

    init {
        index(false, userId) // name = user_key
    }

}

/** A cooldown in context of (messageChannelId) **/
@CreateTable
@Cacheable
object ChannelCooldown : Table("channel_cooldown") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id").nullable()
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId)
}
@CreateTable
@Cacheable
object ChannelUseLimitHistory : Table("channel_use_limit_history") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id").nullable()
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, moment, type)

    init {
        index(false, channelId) // name = channel_key
    }
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
object GuildUseLimitHistory : Table("guild_use_limit_history") {

    val guildId = ulong("guild_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, moment, type)

    init {
        index(false, guildId) // name = guild_key
    }
}


/** A cooldown in context of (guildId, userId) **/
@CreateTable
@Cacheable
object GuildUserCooldown : Table("guild_user_cooldown") {

    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}

@CreateTable
@Cacheable
object GuildUserUseLimitHistory : Table("guild_user_use_limit_history") {

    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, moment, type)

    init {
        index(false, guildId, userId) // name = guild_user_key
    }
}

/** A cooldown in context of (guildId, userId, commandId) **/
@CreateTable
@Cacheable
object GuildUserCommandCooldown : Table("guild_user_command_cooldown") {

    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val commandId = ulong("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, commandId)
}

@CreateTable
@Cacheable
object GuildUserCommandUseLimitHistory : Table("guild_user_command_use_limit_history") {
    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val commandId = ulong("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, commandId, moment)

    init {
        index(false, guildId, userId, commandId) // name = guild_user_command_key
        index(false, guildId, userId) // name = guild_user_key
    }
}

/** A cooldown in context of (channelId, commandId) **/
@CreateTable
@Cacheable
object ChannelCommandCooldown : Table("channel_command_cooldown") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val commandId = ulong("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, commandId)
}

@CreateTable
@Cacheable
object ChannelCommandUseLimitHistory : Table("channel_command_use_limit_history") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val commandId = ulong("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, commandId, moment)
}

/** A cooldown in context of (channelId, userId, commandId) **/
@CreateTable
@Cacheable
object ChannelUserCommandCooldown : Table("channel_user_command_cooldown") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val commandId = ulong("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, userId, commandId)
}

@CreateTable
@Cacheable
object ChannelUserCommandUseLimitHistory : Table("channel_user_command_use_limit_history") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val commandId = ulong("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, userId, commandId)
}

/** A cooldown in context of (channelId, userId) **/
@CreateTable
@Cacheable
object ChannelUserCooldown : Table("channel_user_cooldown") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val until = timestamp("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, userId)
}

@CreateTable
@Cacheable
object ChannelUserUseLimitHistory : Table("channel_user_use_limit_history") {

    val channelId = ulong("channel_id")
    val guildId = ulong("guild_id")
    val userId = ulong("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, userId, moment)
}

/** A cooldown in context of (guildId, commandId) **/
@CreateTable
@Cacheable
object GuildCommandCooldown : Table("guild_command_cooldown") {

    val guildId = ulong("guild_id")
    val commandId = ulong("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, commandId)
}

@CreateTable
@Cacheable
object GuildCommandUseLimitHistory : Table("guild_command_use_limit_history") {

    val guildId = ulong("guild_id")
    val commandId = ulong("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, commandId, moment, type)
}