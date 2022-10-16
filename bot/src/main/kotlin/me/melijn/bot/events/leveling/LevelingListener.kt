package me.melijn.bot.events.leveling

import com.kotlindiscord.kord.extensions.utils.any
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.async.SafeList
import kotlin.time.Duration.Companion.seconds

@Inject(true)
class LevelingListener {

    val logger by Log
    private val xpManager by inject<XPManager>()
    private val activeTimers = SafeList<Snowflake>()

    init {
        val kord by inject<Kord>()
        kord.on<MessageCreateEvent> {
            handle(this)
        }
        kord.on<VoiceStateUpdateEvent> {
            val joinedChannelId = this.state.channelId
            val oldChannelId = this.old?.channelId
            val userId = this.state.userId
            if (oldChannelId != joinedChannelId && joinedChannelId != null){
                // joined a channel

                if (!activeTimers.any { snowflake -> snowflake == userId }) {
                    Scheduler().schedule(60.seconds, callback = {
                        val member = this.state.getMember()
                        val vs = member.getVoiceStateOrNull()
                        val vcEmpty = vs?.getChannelOrNull()?.voiceStates?.any { it.userId != member.id } == false
                        if (vs == null || vs.channelId != joinedChannelId || vs.isDeafened || vcEmpty) {
                            // give no xp
                            activeTimers.remove(userId)
                            return@schedule
                        }

                        // give xp
                        xpManager.increaseGlobalXP(member.id, 5UL)
                        activeTimers.remove(userId)
                        logger.info { "${member.username} gained 5 xp" }

                    })
                    activeTimers.add(userId)
                }
            }else if (oldChannelId != null && joinedChannelId == null) {
                // left a channel
                activeTimers.remove(userId)
            }
        }
    }

    private suspend fun handle(event: MessageCreateEvent) {
        val member = event.member?.takeIf { !it.isBot }
        val userId = member?.id ?: return

        val cooldown = xpManager.getMsgXPCooldown(userId)
        if (cooldown < System.currentTimeMillis()) {
            xpManager.increaseGlobalXP(userId, 1UL)
            xpManager.setMsgXPCooldown(userId, 30.seconds)
            logger.info { "${member.username} gained 1 xp" }
        }
    }
}