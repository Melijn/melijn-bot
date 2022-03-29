package database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractUserCookieManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class UserCookieManager(driverManager: DriverManager) : AbstractUserCookieManager(driverManager) {



}