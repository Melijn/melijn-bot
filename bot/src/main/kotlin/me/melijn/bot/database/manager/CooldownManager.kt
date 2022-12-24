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
@Inject
class GuildUserCommandCooldownManager(override val driverManager: DriverManager)
    : AbstractGuildUserCommandCooldownManager(driverManager)
@Inject
class ChannelCommandCooldownManager(override val driverManager: DriverManager)
    : AbstractChannelCommandCooldownManager(driverManager)
@Inject
class ChannelUserCommandCooldownManager(override val driverManager: DriverManager)
    : AbstractChannelUserCommandCooldownManager(driverManager)
@Inject
class ChannelUserCooldownManager(override val driverManager: DriverManager)
    : AbstractChannelUserCooldownManager(driverManager)
@Inject
class GuildCommandCooldownManager(override val driverManager: DriverManager)
    : AbstractGuildCommandCooldownManager(driverManager)

/** Provides a centralized api for storing, retrieving and updating cooldown types. **/
@Inject
class CooldownManager(
    private val userCooldownManager: UserCooldownManager,
    private val userCommandCooldownManager: UserCommandCooldownManager,
    private val guildUserCooldownManager: GuildUserCooldownManager,
    private val channelCooldownManager: ChannelCooldownManager,
    private val guildCooldownManager: GuildCooldownManager,
    private val guildUserCommandCooldownManager: GuildUserCommandCooldownManager,
    private val channelCommandCooldownManager: ChannelCommandCooldownManager,
    private val channelUserCommandCooldownManager: ChannelUserCommandCooldownManager,
    private val channelUserCooldownManager: ChannelUserCooldownManager,
    private val guildCommandCooldownManager: GuildCommandCooldownManager,
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

    /** (Channel) Cooldown context **/
    fun getChannelCd(channelId: ULong): ChannelCooldownData? {
        return channelCooldownManager.getById(channelId)
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

    /** (Guild, User) Cooldown context **/
    fun getGuildUserCd(guildId: ULong, userId: ULong): GuildUserCooldownData? {
        return guildUserCooldownManager.getById(guildId, userId)
    }

    fun storeGuildUserCd(data: GuildUserCooldownData) {
        guildUserCooldownManager.store(data)
    }

    /** (Guild, User, Command) Cooldown context **/
    fun getGuildUserCmdCd(guildId: ULong, userId: ULong, commandId: Int): GuildUserCommandCooldownData? {
        return guildUserCommandCooldownManager.getById(guildId, userId, commandId)
    }

    fun storeGuildUserCmdCd(data: GuildUserCommandCooldownData) {
        guildUserCommandCooldownManager.store(data)
    }

    /** (Channel, Command) Cooldown context **/
    fun getChannelCmdCd(channelId: ULong, commandId: Int): ChannelCommandCooldownData? {
        return channelCommandCooldownManager.getById(channelId, commandId)
    }

    fun storeChannelCmdCd(data: ChannelCommandCooldownData) {
        channelCommandCooldownManager.store(data)
    }

    /** (Channel, User, Command) Cooldown context **/
    fun getChannelUserCmdCd(channelId: ULong, userId: ULong, commandId: Int): ChannelUserCommandCooldownData? {
        return channelUserCommandCooldownManager.getById(channelId, userId, commandId)
    }

    fun storeChannelUserCmdCd(data: ChannelUserCommandCooldownData) {
        channelUserCommandCooldownManager.store(data)
    }

    /** (Channel, User) Cooldown context **/
    fun getChannelUserCd(channelId: ULong, userId: ULong): ChannelUserCooldownData? {
        return channelUserCooldownManager.getById(channelId, userId)
    }

    fun storeChannelUserCd(data: ChannelUserCooldownData) {
        channelUserCooldownManager.store(data)
    }

    /** (Guild, Command) Cooldown context **/
    fun getGuildCmdCd(guildId: ULong, commandId: Int): GuildCommandCooldownData? {
        return guildCommandCooldownManager.getById(guildId, commandId)
    }

    fun storeGuildCmdCd(data: GuildCommandCooldownData) {
        guildCommandCooldownManager.store(data)
    }
}