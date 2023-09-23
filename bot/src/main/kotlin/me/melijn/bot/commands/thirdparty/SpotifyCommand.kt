@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package me.melijn.bot.commands.thirdparty

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.coroutines.await
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.keysToMap
import me.melijn.bot.web.api.MySpotifyApi
import me.melijn.bot.web.api.WebManager
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.RichPresence
import org.koin.core.component.inject
import java.awt.Color
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


    override suspend fun setup() {
        publicSlashCommand(::SpotifyArguments) {
            name = "spotify"
            description = "shows the target's playing song information"

            check {
                requireBotPermissions(Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)
                anyGuild()
            }

            action {
                val target = arguments.target ?: user

                /**
                 * fetch full discord member which can have spotify presences since
                 * we don't cache or store user presences
                 **/
                val targetMember = guild?.retrieveMembers(true, listOf(target))
                    ?.await()?.firstOrNull()

                // get spotify presences
                val spotifyActivity = targetMember?.activities?.firstNotNullOfOrNull {
                    it.asRichPresence()
                } ?: run {
                    respond {
                        embed { title = "No spotify status found." }
                    }
                    return@action
                }
                val songName = spotifyActivity.details

                val spotifyData = getSaturatedSpotifyDataFromPresence(targetMember, spotifyActivity)

                // craft response
                respond {
                    embed {
                        spotifyData.run {
                            title = target.name + " listening to spotify"
                            thumbnail = songIcon
                            color = Color(30, 215, 96).rgb
                            description = """
                                **[$songName]($songLink)**
                                by ${authorLinkMap.entries.joinToString { "[`${it.key}`](${it.value})" }}
                                on [`$albumName`]($albumLink)
                            """.trimIndent()
                            footer {
                                name = "$songProgress  $songProgressBar  $songLength"
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        /**
         * Parses the presence from [member], searches the spotify track information from presence info
         * @param member should have presences enabled
         * @param spotifyApi api with usable session
         *
         * @return spotify [se.michaelthelin.spotify.model_objects.specification.Track] object if we found a result otherwise null
         */
        suspend fun getSpotifyTrackFromMemberWithPresence(
            member: Member,
            spotifyApi: MySpotifyApi
        ): se.michaelthelin.spotify.model_objects.specification.Track? {
            // get spotify presences
            val presences = member.activities
            val possibleSpotifyActivities = presences.filter { presence ->
                presence.name == "Spotify" && presence.isRich
            }
            val spotifyActivity = possibleSpotifyActivities.firstOrNull()?.asRichPresence() ?: return null
            val songName = spotifyActivity.details
            val songAuthors = spotifyActivity.state?.split("; ") ?: emptyList()

            val searchTerm = buildList {
                songName?.let { add(it) }
                songAuthors.firstOrNull()?.let { add(it) }
            }.joinToString(" ")

            return spotifyApi.searchTrack(searchTerm)
        }
    }

    private suspend fun getSaturatedSpotifyDataFromPresence(
        member: Member?,
        spotifyActivity: RichPresence
    ): SaturatedSpotifyDiscordPresence {
        /** extract info from the presence **/
        val songName = spotifyActivity.details
        val albumName =spotifyActivity.largeImage?.text
        val songAuthors = spotifyActivity.state?.split("; ") ?: emptyList()
        val spotifyThumbnailId = spotifyActivity.largeImage?.key?.drop(8)

        val spotifyApi = webManager.spotifyApi

        /** fetch extra info using the spotify api **/
        val track = if (member != null && spotifyApi != null) {
            getSpotifyTrackFromMemberWithPresence(member, spotifyApi)
        } else null

        val spotifyTrackLink = track?.externalUrls?.externalUrls?.get("spotify")
        val spotifyAlbumLink = track?.album?.externalUrls?.externalUrls?.get("spotify")
        val authorLink = { name: String ->
            track?.artists?.firstOrNull { it.name.equals(name, true) }
                ?.externalUrls?.externalUrls?.get("spotify")
        }

        /** time and progress bar calculations **/
        val created = System.currentTimeMillis()
        val start = spotifyActivity.timestamps?.start ?: created
        val end = spotifyActivity.timestamps?.end ?: created

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