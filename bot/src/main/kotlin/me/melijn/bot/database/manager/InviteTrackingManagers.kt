package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractInvitesManager
import me.melijn.gen.database.manager.AbstractMemberJoinTrackingManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class InvitesManager(override val driverManager: DriverManager): AbstractInvitesManager(driverManager) {

}

@Inject
class MemberJoinTrackingManager(override val driverManager: DriverManager): AbstractMemberJoinTrackingManager(driverManager) {

}