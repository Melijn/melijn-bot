package me.melijn.bot.events.leveling

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.minn.jda.ktx.coroutines.await
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.LEVEL_LOG_BASE
import me.melijn.bot.commands.LevelingExtension
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.LevelRolesManager
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.utils.JDAUtil.awaitOrNull
import me.melijn.bot.utils.KoinUtil
import net.dv8tion.jda.api.Permission

@Inject(true)
class XPChangeListener {

    init {
        val shardManager by KoinUtil.inject<ExtensibleBot>()
        val xpManager by KoinUtil.inject<XPManager>()
        val levelRoleManager by KoinUtil.inject<LevelRolesManager>()
        val settingsManager by KoinUtil.inject<GuildSettingsManager>()

        shardManager.on<GuildXPChangeEvent> {
            println(this)

            if (!this.guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) return@on
            if (!settingsManager.get(guild).enableLeveling) return@on

            val oldLevel = LevelingExtension.getLevel(oldXP, LEVEL_LOG_BASE)
            val newLevel = LevelingExtension.getLevel(newXP, LEVEL_LOG_BASE)

            handleTopRole(xpManager, newLevel)

            if (newLevel > oldLevel) {

                println("User ${user.effectiveName} leveled up $oldLevel -> $newLevel")
                val levelRolesData = levelRoleManager.getById(guild.idLong, newLevel) ?: return@on
                val role = guild.getRoleById(levelRolesData.roleId) ?: return@on

                guild.addRoleToMember(user, role).reason("Levelrole").queue()

                if (levelRolesData.stay) return@on

                val previousLevelRoleData = levelRoleManager.getPrevLevelRole(guild.idLong, newLevel) ?: return@on
                val previousLevelRole = guild.getRoleById(previousLevelRoleData.roleId) ?: return@on

                guild.removeRoleFromMember(user, previousLevelRole).reason("LevelRole has stay=False").queue()
            }
        }
    }

    private suspend fun GuildXPChangeEvent.handleTopRole(
        xpManager: XPManager,
        newLevel: Long
    ) {
        val topRoles = xpManager.getTopRoles(guild.idLong, newLevel)
        val posPair = xpManager.getGuildPosition(guild.idLong, user.idLong) ?: return
        val (_, pos) = posPair

        var min = 0
        val possibleTopRole = topRoles
            .firstOrNull {
                if (pos in 0..it.memberCount) {
                    true
                } else {
                    min += it.memberCount
                    false
                }
            }

        if (possibleTopRole != null) {
            val role = guild.getRoleById(possibleTopRole.roleId) ?: return
            if (!member.roles.contains(role)) {
                guild.addRoleToMember(user, role).reason("Toprole").queue()
                val toExitTopRole = xpManager.getMemberAtPos(guild.idLong, possibleTopRole.memberCount)
                    ?.takeIf { !it.missing } ?: return
                val exitedMember = guild.retrieveMemberById(toExitTopRole.userId).awaitOrNull()
                if (exitedMember == null) {
                    // Unmarking missing members is done when they gain xp via XPManager
                    xpManager.markMemberMissing(toExitTopRole)
                } else {
                    if (exitedMember.roles.contains(role)) {
                        guild.removeRoleFromMember(exitedMember, role).reason("Lost topRole requirement").await()
                    }
                }
            }
        }
        return
    }
}