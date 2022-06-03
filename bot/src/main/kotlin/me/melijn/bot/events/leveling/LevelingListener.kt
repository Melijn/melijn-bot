package me.melijn.bot.events.leveling

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import me.melijn.ap.injector.Inject
import me.melijn.bot.database.manager.XPManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import kotlin.time.Duration.Companion.seconds

@Inject(true)
class LevelingListener {

    init {
        val kord by inject<Kord>()
        kord.on<MessageCreateEvent> {
            handle(this)
        }
    }

    val logger by Log
    private val xpManager by inject<XPManager>()
    private suspend fun handle(event: MessageCreateEvent) {
        val member = event.member?.takeIf { !it.isBot }
        val userId = member?.id ?: return
        if (xpManager.getMsgXPCooldown(userId) < System.currentTimeMillis()) {
            xpManager.increaseGlobalXP(userId, 1UL)
            xpManager.setMsgXPCooldown(userId, System.currentTimeMillis() + 30.seconds.inWholeMilliseconds)
            logger.info { "${member.username} gained 1 xp" }
        }


    }
}