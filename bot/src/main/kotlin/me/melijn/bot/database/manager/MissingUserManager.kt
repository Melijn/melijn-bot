package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.gen.DeletedUsersData
import me.melijn.gen.MissingMembersData
import me.melijn.gen.database.manager.AbstractDeletedUsersManager
import me.melijn.gen.database.manager.AbstractMissingMembersManager
import me.melijn.kordkommons.database.DriverManager

@Inject
class MissingMemberManager(driverManager: DriverManager) : AbstractMissingMembersManager(driverManager)
@Inject
class DeletedUserManager(driverManager: DriverManager) : AbstractDeletedUsersManager(driverManager)
@Inject
class MissingUserManager(
    private val missingMemberManager: MissingMemberManager,
    private val deletedUserManager: DeletedUserManager
)  {
    suspend fun markMemberMissing(guildId: Long, memberId: Long) {
        missingMemberManager.store(MissingMembersData(guildId, memberId))
    }
    suspend fun markMemberPresent(guildId: Long, memberId: Long) {
        missingMemberManager.deleteById(guildId, memberId)
    }

    suspend fun markUserDeleted(userId: Long) {
        deletedUserManager.store(DeletedUsersData(userId))
    }
    suspend fun markUserReinstated(userId: Long) {
        deletedUserManager.deleteById(userId)
    }
}