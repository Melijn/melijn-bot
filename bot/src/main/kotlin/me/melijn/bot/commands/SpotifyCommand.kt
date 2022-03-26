@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.common.entity.optional.value
import dev.kord.core.behavior.requestMembers
import dev.kord.core.cache.data.ActivityData
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.annotationprocessors.command.KordExtension
import me.melijn.bot.web.api.WebManager
import org.jetbrains.kotlin.utils.keysToMap
import org.koin.core.component.inject
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.roundToInt

@KordExtension
class SpotifyCommand : Extension() {

    override val name: String = "spotify"
    private val webManager: WebManager by inject()

    inner class SpotifyArguments : Arguments() {
        val target by optionalUser {
            name = "user"
            description = "Person you'd like to view"
        }
    }

    @OptIn(PrivilegedIntent::class)
    override suspend fun setup() {
        publicSlashCommand(::SpotifyArguments) {
            name = "spotify"
            description = "shows the target's playing song information"

            check {
                requireBotPermissions(Permission.SendMessages, Permission.EmbedLinks)
                anyGuild()
            }

            action {
                val target = arguments.target ?: user.asUser()
                val userId = target.id

                /**
                 * fetch full discord member which can have spotify presences since
                 * we don't cache or store user presences
                 * **/
                val targetMember = this.guild?.requestMembers {
                    this.userIds.add(userId)
                    presences = true
                }?.firstOrNull()

                /** get spotify presences **/
                val presences = targetMember?.data?.presences?.value
                val possibleSpotifyActivities = presences?.mapNotNull { presence ->
                    presence.activities.firstOrNull { it.name == "Spotify" }
                } ?: emptyList()
                val spotifyActivity = possibleSpotifyActivities.firstOrNull() ?: run {
                    respond {
                        embed { title = "No spotify status found." }
                    }
                    return@action
                }

                val spotifyData = getSaturatedSpotifyDataFromPresence(spotifyActivity)

                /** craft response **/
                respond {
                    embed {
                        spotifyData.run {
                            title = target.username + " listening to spotify"
                            thumbnail {
                                if (songIcon != null) url = songIcon
                            }
                            color = Color(30, 215, 96)
                            description = """
                                **[$songName]($songLink)**
                                by ${authorLinkMap.entries.joinToString { "[`${it.key}`](${it.value})" }}
                                on [`$albumName`]($albumLink)
                            """.trimIndent()
                            footer {
                                text = "$songProgress  $songProgressBar  $songLength"
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun getSaturatedSpotifyDataFromPresence(
        spotifyActivity: ActivityData
    ): SaturatedSpotifyDiscordPresence {
        /** extract info from the presence **/
        val songName = spotifyActivity.details.value
        val albumName = spotifyActivity.assets.value?.largeText?.value
        val songAuthors = spotifyActivity.state.value?.split("; ") ?: emptyList()
        val spotifyThumbnailId = spotifyActivity.assets.value?.largeImage?.value?.drop(8)

        val searchTerm = buildList {
            songName?.let { add(it) }
            songAuthors.firstOrNull()?.let { add(it) }
        }.joinToString(" ")

        /** fetch extra info using the spotify api **/
        val track = try {
            webManager.spotifyApi?.searchTrack(searchTerm)
        } catch (t: Throwable) {
            null
        }
        val spotifyTrackLink = track?.externalUrls?.externalUrls?.get("spotify")
        val spotifyAlbumLink = track?.album?.externalUrls?.externalUrls?.get("spotify")
        val authorLink = { name: String ->
            track?.artists?.firstOrNull { it.name.equals(name, true) }
                ?.externalUrls?.externalUrls?.get("spotify")
        }

        /** time and progress bar calculations **/
        val created = System.currentTimeMillis()
        val start = spotifyActivity.timestamps.value?.start.value ?: created
        val end = spotifyActivity.timestamps.value?.end.value ?: created
        val lengthMillis = end - start
        val progressMillis = created - start
        var percent = progressMillis.toDouble() / lengthMillis.toDouble()
        percent = min(100.0, max(0.0, percent * 100.0))

        val progressLength = 20
        val blockCount = percent.roundToInt() / (100 / progressLength)

        val progressMinutes = progressMillis / 1000 / 60
        val progressSeconds = progressMillis / 1000 % 60
        val lengthMinutes = lengthMillis / 1000 / 60
        val lengthSeconds = lengthMillis / 1000 % 60

        val songProgress = String.format("%d:%02d", progressMinutes, progressSeconds)
        val songLength = String.format("%d:%02d", lengthMinutes, lengthSeconds)
        val progressBar = "■".repeat(blockCount) + "□".repeat(progressLength - blockCount)

        return SaturatedSpotifyDiscordPresence(
            "https://i.scdn.co/image/$spotifyThumbnailId",
            songName,
            spotifyTrackLink,
            songAuthors.keysToMap(authorLink),
            albumName,
            spotifyAlbumLink,
            songProgress,
            progressBar,
            songLength
        )
    }
}

data class SaturatedSpotifyDiscordPresence(
    val songIcon: String?,
    val songName: String?,
    val songLink: String?,

    val authorLinkMap: Map<String, String?>,
    val albumName: String?,
    val albumLink: String?,

    val songProgress: String,
    val songProgressBar: String,
    val songLength: String
)