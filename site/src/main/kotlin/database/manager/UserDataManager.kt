package database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.database.manager.AbstractUserDataManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class UserDataManager(override val driverManager: DriverManager) : AbstractUserDataManager(driverManager) {

}