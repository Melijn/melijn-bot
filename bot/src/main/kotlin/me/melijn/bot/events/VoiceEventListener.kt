package me.melijn.bot.events

import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.VoiceManager
import me.melijn.bot.utils.KoinUtil
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class VoiceEventListener {

    private val voiceManager by KoinUtil.inject<VoiceManager>()
    private val guildSettingsManager by KoinUtil.inject<GuildSettingsManager>()


    init {
        val shardManager by KoinUtil.inject<ShardManager>()
        shardManager.listener<GuildVoiceUpdateEvent> { event ->
            val guild = event.guild.idLong

            if (!guildSettingsManager.get(event.guild).allowVoiceTracking) {
                return@listener
            }

            val member = event.member.idLong

            event.channelLeft?.let { channel ->
                voiceManager.insertLeaveNow(guild, channel.idLong, member)
            }
            event.channelJoined?.let { channel ->
                voiceManager.insertJoinNow(guild, channel.idLong, member)
            }
        }
    }

}