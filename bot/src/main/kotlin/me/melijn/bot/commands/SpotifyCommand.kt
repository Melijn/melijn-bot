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
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.bot.model.WebManager
import org.koin.core.component.inject
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.roundToInt

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
            val cmd = this
            name = "spotify"
            description = "spot"
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
                val firstActivity = possibleSpotifyActivities.firstOrNull() ?: run {
                    respond {
                        embed { title = "No spotify status found." }
                    }
                    return@action
                }

                /** extract info from the presence **/
                val songName = firstActivity.details.value
                val albumName = firstActivity.assets.value?.largeText?.value
                val songAuthors = firstActivity.state.value?.split("; ") ?: emptyList()
                val spotifyThumbnailId = firstActivity.assets.value?.largeImage?.value?.drop(8)

                val searchTerm = buildList {
                    songName?.let { add(it) }
                    songAuthors.firstOrNull()?.let { add(it) }
                }.joinToString(" ")

                /** fetch extra info using the spotify api **/
                val track = webManager.spotifyApi?.searchTrack(searchTerm)
                val spotifyTrackLink = track?.externalUrls?.externalUrls?.get("spotify")
                val spotifyAlbumLink = track?.album?.externalUrls?.externalUrls?.get("spotify")
                val authorLink = { name: String ->
                    track?.artists?.firstOrNull { it.name.equals(name, true) }
                        ?.externalUrls?.externalUrls?.get("spotify")
                }

                /** time and progress bar calculations **/
                val created = System.currentTimeMillis()
                val start = firstActivity.timestamps.value?.start.value ?: created
                val end = firstActivity.timestamps.value?.end.value ?: created
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

                val songProgress = "$progressMinutes:$progressSeconds"
                val songLength = "$lengthMinutes:$lengthSeconds"
                val progressBar = "■".repeat(blockCount) + "□".repeat(progressLength - blockCount)

                /** craft response **/
                respond {
                    embed {
                        title = target.username + " listening to spotify"
                        thumbnail {
                            url = "https://i.scdn.co/image/$spotifyThumbnailId"
                        }
                        color = Color(30, 215, 96)
                        description = """
                                **[$songName]($spotifyTrackLink)**
                                by ${songAuthors.joinToString { "[`$it`](${authorLink(it)})" }}
                                on [`$albumName`]($spotifyAlbumLink)
                            """.trimIndent()
                        footer {
                            text = "$songProgress  $progressBar  $songLength"
                        }
                    }
                }
            }
        }
    }
}