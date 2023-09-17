package me.melijn.bot.events.leveling

import com.kotlindiscord.kord.extensions.ExtensibleBot
import me.melijn.ap.injector.Inject
import me.melijn.bot.commands.LEVEL_LOG_BASE
import me.melijn.bot.commands.LevelingExtension
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.LevelRolesManager
import me.melijn.bot.database.manager.XPManager
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
            val topRoles = xpManager.getTopRoles(guild.idLong, newLevel)
            val posPair = xpManager.getGuildPosition(guild.idLong, user.idLong) ?: return@on
            val (xpData, pos) = posPair


//            val possibleTopRoles = xpManager.get(guild.idLong, user.idLong)

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
                val role = guild.getRoleById(possibleTopRole.roleId) ?: return@on
                if (!member.roles.contains(role)) {
                    guild.addRoleToMember(user, role).reason("Toprole").queue()
                    println(possibleTopRole)
                }
            }

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
}