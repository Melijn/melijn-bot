package me.melijn.bot.database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.duration
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

@CreateTable
@Cacheable
object Invites : Table("invites") {

    var inviteCode = text("invite_code") // invite code: discord.gg/{inviteCode}
    var guildId = long("guild_id")
    var channelId = long("channel_id") // invite target channel
    var userId = long("user_id") // invite owner
    var uses = integer("uses") // invite uses
    var createdAt = timestamp("created_at") // invite creation time
    var expiry = duration("expiry").nullable() // invite expiry
    var deleted = bool("deleted") // invite deleted

    override val primaryKey: PrimaryKey = PrimaryKey(inviteCode, guildId)

    init {
        index(false, guildId, deleted) // name = guild_deleted_key
        index(false, guildId, uses) // name = guild_uses_key
        index(false, guildId, userId) // name = guild_user_key
        index(false, guildId, channelId) // name = guild_channel_key
    }
}

@CreateTable
@Cacheable
object MemberJoinTracking : Table("member_join_tracking") {

    var guildId = long("guild_id")
    var userId = long("user_id")
    var inviteCode = text("used_invite_code") // invite code: discord.gg/{inviteCode}
    var firstJoinTime = timestamp("first_join_time") // invite target channel
    var joins = long("join_count") // invite target channel

    override val primaryKey: PrimaryKey = PrimaryKey(guildId, userId)

    init {
        index(true, guildId) // name = guild_user_key
        index(false, guildId, userId) // name = guild_user_key
    }
}