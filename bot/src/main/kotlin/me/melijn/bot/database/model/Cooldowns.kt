package me.melijn.bot.database.model

import me.melijn.apredgres.createtable.CreateTable
import me.melijn.apredgres.tablemodel.TableModel
import me.melijn.botannotationprocessors.uselimit.UseLimit
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** This does only hold normal command uses, not ratelimit hits or cooldown hits **/
@CreateTable
@UseLimit(UseLimit.TableType.HISTORY)
@TableModel(true)
object UsageHistory : Table("usage_history") {

    val guildId = long("guild_id").nullable()
    val channelId = long("channel_id")
    val userId = long("user_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, userId, moment)

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
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object UserCommandCooldown : Table("user_command_cooldown") {

    val userId = long("user_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, commandId)
}
@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object UserCommandUseLimitHistory : Table("user_command_use_limit_history") {

    val userId = long("user_id")
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
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object UserCooldown : Table("user_cooldown") {

    val userId = long("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}
@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object UserUseLimitHistory : Table("user_use_limit_history") {

    val userId = long("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(userId, moment, type)

    init {
        index(false, userId) // name = user_key
    }

}

/** A cooldown in context of (messageChannelId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object ChannelCooldown : Table("channel_cooldown") {

    val channelId = long("channel_id")
    val guildId = long("guild_id").nullable()
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId)
}
@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object ChannelUseLimitHistory : Table("channel_use_limit_history") {

    val channelId = long("channel_id")
    val guildId = long("guild_id").nullable()
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, moment, type)

    init {
        index(false, channelId) // name = channel_key
    }
}

/** A cooldown in context of (guildId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object GuildCooldown : Table("guild_cooldown") {

    val guildId = long("guild_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object GuildUseLimitHistory : Table("guild_use_limit_history") {

    val guildId = long("guild_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, moment, type)

    init {
        index(false, guildId) // name = guild_key
    }
}


/** A cooldown in context of (guildId, userId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object GuildUserCooldown : Table("guild_user_cooldown") {

    val guildId = long("guild_id")
    val userId = long("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object GuildUserUseLimitHistory : Table("guild_user_use_limit_history") {

    val guildId = long("guild_id")
    val userId = long("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, moment, type)

    init {
        index(false, guildId, userId) // name = guild_user_key
    }
}

/** A cooldown in context of (guildId, userId, commandId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object GuildUserCommandCooldown : Table("guild_user_command_cooldown") {

    val guildId = long("guild_id")
    val userId = long("user_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId, commandId)

    init {
        index(false, guildId, userId, commandId) // name = guild_user_command_key
    }
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object GuildUserCommandUseLimitHistory : Table("guild_user_command_use_limit_history") {
    val guildId = long("guild_id")
    val userId = long("user_id")
    val commandId = integer("command_id")
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
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object ChannelCommandCooldown : Table("channel_command_cooldown") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, commandId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object ChannelCommandUseLimitHistory : Table("channel_command_use_limit_history") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, commandId, moment)

    init {
        index(false, channelId, commandId) // name = channel_command_key
    }
}

/** A cooldown in context of (channelId, userId, commandId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object ChannelUserCommandCooldown : Table("channel_user_command_cooldown") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val userId = long("user_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, userId, commandId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object ChannelUserCommandUseLimitHistory : Table("channel_user_command_use_limit_history") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val userId = long("user_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, userId, commandId)

    init {
        index(false, channelId, userId, commandId) // name = channel_user_command_key
    }
}

/** A cooldown in context of (channelId, userId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object ChannelUserCooldown : Table("channel_user_cooldown") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val userId = long("user_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, userId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object ChannelUserUseLimitHistory : Table("channel_user_use_limit_history") {

    val channelId = long("channel_id")
    val guildId = long("guild_id")
    val userId = long("user_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(channelId, guildId, userId, moment)

    init {
        index(false, channelId, userId) // name = channel_user_key
    }
}

/** A cooldown in context of (guildId, commandId) **/
@CreateTable
@UseLimit(UseLimit.TableType.COOLDOWN)
@TableModel(true)
object GuildCommandCooldown : Table("guild_command_cooldown") {

    val guildId = long("guild_id")
    val commandId = integer("command_id")
    val until = long("until")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, commandId)
}

@CreateTable
@UseLimit(UseLimit.TableType.LIMIT_HIT)
@TableModel(true)
object GuildCommandUseLimitHistory : Table("guild_command_use_limit_history") {

    val guildId = long("guild_id")
    val commandId = integer("command_id")
    val moment = timestamp("moment")
    val type = enumeration<UseLimitHitType>("hit_type")

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, commandId, moment, type)

    init {
        index(false, guildId, commandId) // name = guild_command_key
    }
}