package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractUserCommandCooldownManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class UserCommandCooldownManager(override val driverManager: DriverManager) : AbstractUserCommandCooldownManager(driverManager)
@Inject
class UserCooldownManager(override val driverManager: DriverManager) : AbstractUserCooldownManager(driverManager)