package me.melijn.bot.events

import dev.minn.jda.ktx.events.listener
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.database.manager.VoiceManager
import me.melijn.bot.utils.KoinUtil
import me.melijn.kordkommons.logger.logger
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.concurrent.ConcurrentHashMap

@Inject(true)
class VoiceEventListener {

    private val voiceManager by KoinUtil.inject<VoiceManager>()
    private val guildSettingsManager by KoinUtil.inject<GuildSettingsManager>()

    private val joinTimes = ConcurrentHashMap<UserSnowflake, Instant>()
    private val logger = logger()

    init {
        val shardManager by KoinUtil.inject<ShardManager>()
        shardManager.listener<GuildVoiceUpdateEvent> { event ->
            val guild = event.guild.idLong

            if (!guildSettingsManager.get(event.guild).allowVoiceTracking) {
                return@listener
            }

            val member = event.member.idLong

            event.channelLeft?.let { channel ->
                val joinTime = joinTimes.remove(event.member)
                val timeSpent = joinTime?.let { Clock.System.now() - it }

                voiceManager.insertLeaveNow(guild, channel.idLong, member, timeSpent)
            }

            event.channelJoined?.let { channel ->
                if (joinTimes.put(event.member, Clock.System.now()) != null)
                    logger.warn("${event.member.idLong} joined a voice channel twice without leaving")

                voiceManager.insertJoinNow(guild, channel.idLong, member)
            }
        }
    }

}