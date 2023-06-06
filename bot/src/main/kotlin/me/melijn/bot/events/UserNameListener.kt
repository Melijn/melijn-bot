package me.melijn.bot.events

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.bot.utils.StringsUtil
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class UserNameListener {

    private val guildSettingsManager by KoinUtil.inject<GuildSettingsManager>()

    init {
        val shardManager by KoinUtil.inject<ShardManager>()
        shardManager.listener<GuildMemberJoinEvent> { event ->
            if (shouldNameNormalize(event.guild)) return@listener
            fixUserName(event.member)
        }

        shardManager.listener<GuildMemberUpdateEvent> { event ->
            if (shouldNameNormalize(event.guild)) return@listener
            fixUserName(event.member)
        }
    }

    private suspend fun shouldNameNormalize(guild: Guild): Boolean =
        !guildSettingsManager.get(guild).enableNameNormalization ||
                !guild.selfMember.hasPermission(Permission.NICKNAME_CHANGE)

    private suspend fun fixUserName(member: Member) {
        // if the user has a garbage name,
        val effectiveName = member.effectiveName
        val properName = StringsUtil.filterGarbage(effectiveName)
        if (properName != effectiveName) {
            // change it.
            member.modifyNickname(properName)
                .reason("Name contains extraneous characters.")
                .await()
        }
    }


}
