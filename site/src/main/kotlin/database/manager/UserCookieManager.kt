package database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractUserCookieManager
import me.melijn.kordkommons.database.DriverManager
import util.CookieUtil.isValid

@Inject
class UserCookieManager(driverManager: DriverManager) : AbstractUserCookieManager(driverManager) {

    fun isValidCookie(cookie: String): Boolean {
        val info = getByCookieIndex(cookie) ?: return false
        return isValid(info)
    }

}