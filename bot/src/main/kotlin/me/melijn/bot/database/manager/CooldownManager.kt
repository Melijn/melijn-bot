package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.*
import me.melijn.gen.database.manager.*
import me.melijn.kordkommons.database.DriverManager

@Inject
class UserCommandCooldownManager(override val driverManager: DriverManager)
    : AbstractUserCommandCooldownManager(driverManager)
@Inject
class UserCooldownManager(override val driverManager: DriverManager)
    : AbstractUserCooldownManager(driverManager)
@Inject
class GuildUserCooldownManager(override val driverManager: DriverManager)
    : AbstractGuildUserCooldownManager(driverManager)
@Inject
class ChannelCooldownManager(override val driverManager: DriverManager)
    : AbstractChannelCooldownManager(driverManager)
@Inject
class GuildCooldownManager(override val driverManager: DriverManager)
    : AbstractGuildCooldownManager(driverManager)

/** Provides a centralized api for storing, retrieving and updating cooldown types. **/
@Inject
class CooldownManager(
    private val userCooldownManager: UserCooldownManager,
    private val userCommandCooldownManager: UserCommandCooldownManager,
    private val guildUserCooldownManager: GuildUserCooldownManager,
    private val channelCooldownManager: ChannelCooldownManager,
    private val guildCooldownManager: GuildCooldownManager,
) {

    /** (User Command) Cooldown context **/
    fun getUserCmdCd(userId: ULong, commandId: Int): UserCommandCooldownData? {
        return userCommandCooldownManager.getById(userId, commandId)
    }
    fun storeUserCmdCd(data: UserCommandCooldownData) {
        userCommandCooldownManager.store(data)
    }

    /** (User) Cooldown context **/
    fun getUserCd(userId: ULong): UserCooldownData? {
        return userCooldownManager.getById(userId)
    }

    fun storeUserCd(data: UserCooldownData) {
        userCooldownManager.store(data)
    }

    /** (User) Cooldown context **/
    fun getChannelCd(userId: ULong): ChannelCooldownData? {
        return channelCooldownManager.getById(userId)
    }

    fun storeChannelCd(data: ChannelCooldownData) {
        channelCooldownManager.store(data)
    }

    /** (Guild) Cooldown context **/
    fun getGuildCd(userId: ULong): GuildCooldownData? {
        return guildCooldownManager.getById(userId)
    }

    fun storeGuildCd(data: GuildCooldownData) {
        guildCooldownManager.store(data)
    }

    fun getGuildUserCd(guildId: ULong, userId: ULong): GuildUserCooldownData? {
        return guildUserCooldownManager.getById(guildId, userId)
    }

    fun storeGuildUserCd(data: GuildUserCooldownData) {
        guildUserCooldownManager.store(data)
    }


}