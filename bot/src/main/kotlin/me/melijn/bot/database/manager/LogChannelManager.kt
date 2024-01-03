package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractLogChannelsManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class LogChannelManager(override val driverManager: DriverManager) : AbstractLogChannelsManager(driverManager) {

}