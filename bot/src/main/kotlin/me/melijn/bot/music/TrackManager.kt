package me.melijn.bot.music

import dev.schlaubi.lavakord.audio.Link
import dev.schlaubi.lavakord.audio.TrackEndEvent
import dev.schlaubi.lavakord.audio.on
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.melijn.kordkommons.async.SafeList
import me.melijn.kordkommons.async.Task
import me.melijn.kordkommons.logger.logger
import kotlin.random.Random

class TrackManager(val link: Link) {

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

    private suspend fun onTrackEnd(event: TrackEndEvent) {
        if (event.reason.mayStartNext)
            nextTrack(event.track)
    }

    private suspend fun nextTrack(previousTrack: dev.schlaubi.lavakord.audio.player.Track?) {
        val lastData = playingTrack?.data
        if (queue.isEmpty()) {
            if (loopedQueue || looped) {
                play(playingTrack!!)
                return
            }

            Task {
                link.destroy()
            }.run()

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

        queue.removeFirstAndGetNextOrNull(nextPos)?.let { next ->
            play(next)
        } ?: stopAndDestroy()
    }

    private suspend fun stop() {
        mutex.withLock {
            player.stopTrack()
            playingTrack = null
        }
    }

    private suspend fun stopAndDestroy() {
        queue.clear()
        stop()

        Task {
            link.destroy()
        }.run()
    }
}