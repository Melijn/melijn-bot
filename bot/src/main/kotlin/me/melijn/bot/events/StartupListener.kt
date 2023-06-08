package me.melijn.bot.events


import dev.minn.jda.ktx.events.listener
import me.melijn.ap.injector.Inject
import me.melijn.ap.injector.InjectorInterface
import me.melijn.bot.music.MusicManager
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.bot.utils.Log
import me.melijn.kordkommons.utils.ReflectUtil
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.sharding.ShardManager

@Inject(true)
class StartupListener {

    private val logger by Log
    private val shardManager by inject<ShardManager>()
    private val startedShards = mutableSetOf<Int>()

    init {
        shardManager.listener<ReadyEvent> { event ->
            val shard = event.jda
            val shardId = shard.shardInfo.shardId
            logger.info { "Shard #${shardId} is ready" }

            val isFirstShardStartup = startedShards.add(shardId)
            val isFirstSuccessfulStartup = isFirstShardStartup && startedShards.size == shardManager.shards.size

            if (isFirstSuccessfulStartup) {
                logger.info { "All shards are ready" }
                initAfterStartupMelijnMoules()
            }

            if (isFirstShardStartup) {
                MusicManager.recoverMusic(shard)
            }
        }
    }

    private fun initAfterStartupMelijnMoules() {
        ReflectUtil.getInstanceOfKspClass<InjectorInterface>(
            "me.melijn.gen", "InjectionKoinModule"
        ).initInjects(1)
        logger.info { "Loaded initGroup #1 - the set of injected classes after all shards initialized" }
    }
}