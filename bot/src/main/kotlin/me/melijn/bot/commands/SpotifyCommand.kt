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
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.value
import dev.kord.core.Kord
import dev.kord.core.behavior.requestMembers
import dev.kord.core.cache.data.ActivityData
import dev.kord.core.entity.User
import dev.kord.core.event.guild.MembersChunkEvent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.web.api.MySpotifyApi
import me.melijn.bot.web.api.WebManager
import org.jetbrains.kotlin.utils.keysToMap
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.roundToInt

@Suppress("OPT_IN_IS_NOT_ENABLED")
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
                 **/
                val targetMember = guild?.requestMembers {
                    userIds.add(userId)
                    presences = true
                }?.firstOrNull()

                // get spotify presences
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
                val songName = spotifyActivity.details.value

                val spotifyData = getSaturatedSpotifyDataFromPresence(targetMember, spotifyActivity)

                // craft response
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

    companion object {

        /**
         * Parses the presence from [member], searches the spotify track information from presence info
         * @param member should have presences enabled
         * @param spotifyApi api with usable session
         *
         * @return spotify [se.michaelthelin.spotify.model_objects.specification.Track] object if we found a result otherwise null
         */
        suspend fun getSpotifyTrackFromMemberWithPresence(
            member: MembersChunkEvent,
            spotifyApi: MySpotifyApi
        ): se.michaelthelin.spotify.model_objects.specification.Track? {
            // get spotify presences
            val presences = member.data.presences.value
            val possibleSpotifyActivities = presences?.mapNotNull { presence ->
                presence.activities.firstOrNull { it.name == "Spotify" }
            } ?: emptyList()
            val spotifyActivity = possibleSpotifyActivities.firstOrNull() ?: return null
            val songName = spotifyActivity.details.value
            val songAuthors = spotifyActivity.state.value?.split("; ") ?: emptyList()

            val searchTerm = buildList {
                songName?.let { add(it) }
                songAuthors.firstOrNull()?.let { add(it) }
            }.joinToString(" ")

            return spotifyApi.searchTrack(searchTerm)
        }

        @OptIn(PrivilegedIntent::class)
        /**
         * Parses the presence [user] in [guildId], searches the spotify track information from presence info
         *
         * @param guildId guild in which presence should be checked, user needs to be a member
         * @param user target user
         * @param spotifyApi api with usable session
         *
         * @return spotify [se.michaelthelin.spotify.model_objects.specification.Track] object if we found a result otherwise null
         */
        suspend fun getSpotifyTrackFromUser(
            guildId: Snowflake,
            user: User,
            spotifyApi: MySpotifyApi
        ): se.michaelthelin.spotify.model_objects.specification.Track? {
            val kord by KoinJavaComponent.inject<Kord>(Kord::class.java)

            /**
             * fetch full discord member which can have spotify presences since
             * we don't cache or store user presences
             **/
            return kord.getGuild(guildId)?.requestMembers {
                userIds.add(user.id)
                presences = true
            }?.firstOrNull()?.let {
                getSpotifyTrackFromMemberWithPresence(it, spotifyApi)
            }
        }
    }

    private suspend fun getSaturatedSpotifyDataFromPresence(
        member: MembersChunkEvent?,
        spotifyActivity: ActivityData
    ): SaturatedSpotifyDiscordPresence {
        /** extract info from the presence **/
        val songName = spotifyActivity.details.value
        val albumName = spotifyActivity.assets.value?.largeText?.value
        val songAuthors = spotifyActivity.state.value?.split("; ") ?: emptyList()
        val spotifyThumbnailId = spotifyActivity.assets.value?.largeImage?.value?.drop(8)

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
//        val start = spotifyActivity.timestamps.value?.start?.value?.toEpochMilliseconds() ?: created
        val start = spotifyActivity.timestamps.value?.start?.value ?: created
//        val end = spotifyActivity.timestamps.value?.end?.value?.toEpochMilliseconds() ?: created
        val end = spotifyActivity.timestamps.value?.end?.value ?: created
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