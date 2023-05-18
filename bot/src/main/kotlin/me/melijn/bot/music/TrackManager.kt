package me.melijn.bot.music

import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.audio.TrackEndEvent
import dev.schlaubi.lavakord.audio.on
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.melijn.bot.model.PremiumIntLimit
import me.melijn.bot.model.enums.IntLimit
import me.melijn.bot.model.enums.PremiumTier
import me.melijn.bot.utils.KoinUtil.inject
import me.melijn.kordkommons.async.SafeList
import me.melijn.kordkommons.logger.logger
import net.dv8tion.jda.api.entities.Member
import kotlin.random.Random

class TrackManager(val link: Link) {

    private val trackLoader by inject<TrackLoader>()
    private val mutex = Mutex()

    val player = link.player
    var playingTrack: Track? = null
    val queue = SafeList<Track>(mutex)

    var looped = false
    var loopedQueue = false
    var shuffle = false

    init {
        player.on(consumer = ::onTrackEnd)
    }

    companion object {
        private val MAX_QUEUE_SIZE = PremiumIntLimit(500) {
            when (it) {
                PremiumTier.TIER_1 -> IntLimit(10000)
                PremiumTier.TIER_2 -> IntLimit(20000)
            }
        }
    }

    private suspend fun onTrackEnd(event: TrackEndEvent) {
        if (event.reason.mayStartNext)
            nextTrack(event.getTrack())
    }

    private suspend fun nextTrack(previousTrack: dev.schlaubi.lavakord.audio.player.Track?) {
        val lastData = playingTrack?.data
        if (queue.isEmpty()) {
            if (loopedQueue || looped) {
                play(playingTrack!!)
                return
            }

            val targetVal = target
            if (targetVal != null) {
                playFromTarget(targetVal)
                return
            }

            link.destroy()
            return
        }

        if (looped) {
            play(playingTrack!!)
            return
        }

        val track = queue.removeAtOrNull(0) ?: return
        play(track)

        if (loopedQueue && previousTrack != null) {
            queue(FetchedTrack.fromLavakordTrackWithData(previousTrack, lastData!!), QueuePosition.BOTTOM)
        }
    }

    private val logger = logger()
    suspend fun play(track: Track) {
        val foundTrack = track.getLavakordTrack()
        if (foundTrack == null) {
            // TODO: send track fetch error in music channel
            logger.warn { "Failed to fetch ${track.url}" }
            nextTrack(track.getLavakordTrack())
        } else {
            val fetched = FetchedTrack.fromLavakordTrackWithData(foundTrack, track.data!!)
            mutex.withLock {
                player.playTrack(foundTrack)
                playingTrack = fetched
            }
        }
    }

    suspend fun queue(track: Track, position: QueuePosition) {
        when (position) {
            QueuePosition.BOTTOM -> queue.add(track)
            QueuePosition.RANDOM -> {
                val randomIndex = Random.nextInt(queue.size + 1)
                queue.add(randomIndex, track)
            }

            QueuePosition.TOP -> queue.add(0, track)
            QueuePosition.TOP_SKIP -> {
                queue.add(0, track)
                skip(1)
            }
        }
        if (playingTrack == null) play(queue.removeAt(0))
    }

    suspend fun skip(amount: Int, skipType: SkipType = SkipType.HARD) {
        val queueSize = queue.size
        var nextPos = amount

        if (skipType == SkipType.SOFT && loopedQueue) {
            nextPos = when (amount) {
                in 0..queueSize + 1 -> amount
                else -> amount % (queueSize + 1)
            }

            if (playingTrack == null) return
            if (nextPos == 0) nextPos = queueSize + 1
            val toQueue = listOf(playingTrack!!) + queue.take(nextPos - 1)
            queue.addAll(toQueue)
        }

        val hasNext = queue.removeFirstAndGetNextOrNull(nextPos)?.let { next ->
            play(next)
        } != null
        if (!hasNext) {
            if (target == null) stopAndDestroy()
            else nextTrack(playingTrack?.getLavakordTrack())
        }
    }

    private suspend fun stop() {
        mutex.withLock {
            player.stopTrack()
            playingTrack = null
        }
    }

    suspend fun stopAndDestroy() {
        clear()
        stop()

        link.destroy()
    }

    var target: Member? = null

    fun follow(user: Member?) {
        target = user
    }

    suspend fun clear() {
        queue.clear()
    }

    suspend fun seek(position: Long) {
        player.seekTo(position)
    }

    private suspend fun playFromTarget(target: Member) {
        val track = trackLoader.fetchTrackFromPresence(target.guild, target.user) ?: return
        play(track)
    }

    suspend fun shuffle() {
        queue.shuffle()
    }

    suspend fun getTrackByIndex(trackIndex: Int): Track? {
        return if (trackIndex == 0) playingTrack
        else queue.getOrNull(trackIndex)
    }

    suspend fun getTracksByIndexes(ranges: List<IntRange>): List<Track> {
        val indexes = HashSet<Int>()
        ranges.forEach { range -> for (i in range) indexes.add(i) }
        return queue.getAll(indexes)
    }

    suspend fun getTracksByIndexes(collection: Collection<Int>): List<Track> {
        return queue.getAll(collection)
    }

    var lastBackup = 0L
    var trackBackup: Track? = null
    var positionBackup: Long? = null
    var pausedBackup: Boolean? = null

    fun onPotentialNodeRestart() {
        lastBackup = System.currentTimeMillis()
        trackBackup = playingTrack
        positionBackup = player.position
        pausedBackup = player.paused
    }

    suspend fun onNodeRestarted() {
        val track = trackBackup
        val position = positionBackup
        val paused = pausedBackup

        if (track != null && position != null && paused != null) {
            val vc = link.lastChannelId ?: return

            // reset vc session so the new ll instance can use it
            link.disconnectAudio()
            delay(1000)
            link.connectAudio(vc)
            delay(1000)

            // recover player state
            play(track)
            player.pause(paused)
            player.seekTo(position)
        }
    }
}