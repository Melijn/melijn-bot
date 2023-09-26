package me.melijn.bot.database.manager

import me.melijn.ap.injector.Inject
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.gen.DeletedUsersData
import me.melijn.gen.MissingMembersData
import me.melijn.gen.NoDmsUsersData
import me.melijn.gen.database.manager.AbstractDeletedUsersManager
import me.melijn.gen.database.manager.AbstractMissingMembersManager
import me.melijn.gen.database.manager.AbstractNoDmsUsersManager
import me.melijn.kordkommons.database.DriverManager
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel
import net.dv8tion.jda.api.sharding.ShardManager

@Inject
class MissingMemberManager(driverManager: DriverManager) : AbstractMissingMembersManager(driverManager)

@Inject
class DeletedUserManager(driverManager: DriverManager) : AbstractDeletedUsersManager(driverManager)

@Inject
class NoDmsUserManager(driverManager: DriverManager) : AbstractNoDmsUsersManager(driverManager)

@Inject
class MissingUserManager(
    private val missingMemberManager: MissingMemberManager,
    private val deletedUserManager: DeletedUserManager,
    private val noDmsUserManager: NoDmsUserManager
) {
    suspend fun markMemberMissing(guildId: Long, memberId: Long) {
        missingMemberManager.store(MissingMembersData(guildId, memberId))
    }

    suspend fun markMemberPresent(guildId: Long, memberId: Long) {
        missingMemberManager.deleteById(guildId, memberId)
    }

    suspend fun markUserDmsClosed(userId: Long) {
        noDmsUserManager.store(NoDmsUsersData(userId))
    }

    suspend fun markUserDmsOpen(userId: Long) {
        noDmsUserManager.deleteById(userId)
    }

    suspend fun markUserDeleted(userId: Long) {
        deletedUserManager.store(DeletedUsersData(userId))
    }

    suspend fun markUserReinstated(userId: Long) {
        deletedUserManager.deleteById(userId)
    }

    suspend fun isDeleted(userId: Long): Boolean = deletedUserManager.getById(userId) != null
    suspend fun hasDmsClosed(userId: Long): Boolean = noDmsUserManager.getById(userId) != null


}

suspend fun ShardManager.retrieveUserOrMarkDeleted(userId: Long): User? {
    val missingUserManager: MissingUserManager by inject()
    val isDeleted = missingUserManager.isDeleted(userId)
    if (isDeleted) return null
    val user = retrieveUserById(userId).awaitOrNull()
    if (user == null) {
        missingUserManager.markUserDeleted(userId)
    }
    return user
}

suspend fun ShardManager.openPrivateChannelSafely(userId: Long): PrivateChannel? {
    val missingUserManager: MissingUserManager by inject()
    val hasDmsClosed = missingUserManager.hasDmsClosed(userId)
    if (hasDmsClosed) return null

    val user = retrieveUserOrMarkDeleted(userId) ?: return null
    val privateChannel = user.openPrivateChannel().awaitOrNull()
    if (privateChannel == null) missingUserManager.markUserDmsClosed(userId)
    return privateChannel
}
