package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.UserCommandCooldownData
import me.melijn.gen.UserCooldownData
import me.melijn.gen.database.manager.AbstractUserCommandCooldownManager
import me.melijn.gen.database.manager.AbstractUserCooldownManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class UserCommandCooldownManager(override val driverManager: DriverManager) : AbstractUserCommandCooldownManager(driverManager)
@Inject
class UserCooldownManager(override val driverManager: DriverManager) : AbstractUserCooldownManager(driverManager)

/** Provides a centralized api for storing, retrieving and updating cooldown types. **/
@Inject
class CooldownManager(
    private val userCooldownManager: UserCooldownManager,
    private val userCommandCooldownManager: UserCommandCooldownManager,
) {

    /** User Command Cooldown context **/
    fun getUserCmdCd(userId: ULong, commandId: Int): UserCommandCooldownData? {
        return userCommandCooldownManager.getById(userId, commandId)
    }
    fun storeUserCmdCd(userCommandCooldownData: UserCommandCooldownData) {
        userCommandCooldownManager.store(userCommandCooldownData)
    }

    /** User Cooldown context **/
    fun getUserCd(userId: ULong): UserCooldownData? {
        return userCooldownManager.getById(userId)
    }

    fun storeUserCd(userCommandCooldownData: UserCooldownData) {
        userCooldownManager.store(userCommandCooldownData)
    }
}