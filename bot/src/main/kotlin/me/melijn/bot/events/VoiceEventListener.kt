package me.melijn.bot.events

import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.VoiceManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.kordkommons.logger.logger
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class VoiceEventListener {

    private val voiceManager by KoinUtil.inject<VoiceManager>()
    private val guildSettingsManager by KoinUtil.inject<GuildSettingsManager>()
    private val logger = logger()

    init {
        val shardManager by KoinUtil.inject<ShardManager>()
        shardManager.listener<GuildVoiceUpdateEvent> { event ->
            val guild = event.guild.idLong

            if (!guildSettingsManager.get(event.guild).allowVoiceTracking) {
                return@listener
            }

            val member = event.member.idLong

            event.channelLeft?.let { _ ->
                voiceManager.insertLeaveNow(member)
            }
            event.channelJoined?.let { channel ->
                voiceManager.insertJoinNow(guild, channel.idLong, member)
            }
        }

        // Restore voice states after a bot restart.
        // If a user is currently in voice, and they have a dangling entry in the database (not older than 1 day),
        // we assume that they did not leave voice while the bot was down.
        shardManager.listener<ReadyEvent> {
            for ((guild, channel, user, join) in voiceManager.getDanglingJoins()) {
                val voiceState = shardManager.getGuildById(guild)
                    ?.getMember(UserSnowflake.fromId(user))
                    ?.voiceState ?: continue
                if (voiceState.channel?.idLong == channel)
                    voiceManager.putJoinTime(user, join).also {
                        logger.info("Restored $user's join time from last restart")
                    }
            }
        }
    }

}