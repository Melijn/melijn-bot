package me.melijn.bot.events.leveling

import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.services.Service
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.kordkommons.async.RunnableTask
import me.melijn.kordkommons.async.SafeList
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

@Inject(true)
class LevelingListener : Service("leveling", 60.seconds, 60.seconds, true) {

    private val xpManager by inject<XPManager>()
    private val xpReceivers = SafeList<Pair<Long, Long>>()

    init {
        val kord by inject<ShardManager>()
        kord.listener<MessageReceivedEvent> {
            handle(it)
        }
        kord.listener<GuildVoiceUpdateEvent> {
            val joinedChannelId = it.channelJoined?.idLong
            val oldChannelId = it.channelLeft?.idLong
            val userId = it.member.idLong
            val guildId2 = it.guild.idLong
            if (oldChannelId != joinedChannelId && joinedChannelId != null) {
                // joined a channel

                if (!xpReceivers.any { (guildId, snowflake) -> snowflake == userId && guildId2 == guildId }) {
                    xpReceivers.add(guildId2 to userId)
                }
            } else if (oldChannelId != null && joinedChannelId == null) {
                // left a channel
                xpReceivers.remove(guildId2 to userId)
            }
        }
    }

    private suspend fun handle(event: MessageReceivedEvent) {
        val member = event.member?.takeIf { !it.user.isBot } ?: return
        val user = member.user

        val cooldown = xpManager.getMsgXPCooldown(user)
        if (cooldown < System.currentTimeMillis()) {
            xpManager.increaseAllXP(member, 1L)
            xpManager.setMsgXPCooldown(user, 30.seconds)
            logger.info { "${member.effectiveName} gained 1 xp" }
        }
    }

    override val service: RunnableTask = RunnableTask {
        val kord by inject<ShardManager>()
        xpReceivers.forEach { (guildId, userId) ->
            val member = kord.getGuildById(guildId)?.getMemberById(userId) ?: return@forEach
            val vs = member.voiceState
            val vcEmpty = vs?.channel?.members?.any { it.idLong != member.idLong } == false

            // give no xp
            if (vs == null || vs.channel == null) {
                xpReceivers.remove(guildId to userId)
                return@forEach
            }
            if (vs.isDeafened || vcEmpty) {
                return@forEach
            }

            // give xp
            xpManager.increaseAllXP(member, 5L)
            logger.info { "${member.effectiveName} gained 5 xp" }
        }
    }
}