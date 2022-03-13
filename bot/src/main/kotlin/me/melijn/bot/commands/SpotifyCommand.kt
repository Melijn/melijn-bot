package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.checks.anyGuild
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.requestMembers
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import me.melijn.bot.database.manager.GuildSettingsManager
import me.melijn.bot.model.WebManager
import org.koin.core.component.inject

class SpotifyCommand : Extension() {

    override val name: String = "spotify"
    val guildSettingsManager: GuildSettingsManager by inject()
    val webManager: WebManager by inject()

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

                val targetMember = this.guild?.requestMembers {
                    this.userIds.add(userId)
                    presences = true
                }?.firstOrNull()
                val presences = targetMember?.data?.presences?.value

                val spotifyActivities = presences?.mapNotNull { presence ->
                    presence.activities.firstOrNull { it.name == "Spotify" }
                } ?: emptyList()
                val firstActivity = spotifyActivities.firstOrNull()
                if (firstActivity == null) {
                    this.respond {
                        this.embed {
                            this.title = "No spotify status found."
                        }
                    }
                } else {
                    val track = webManager.spotifyApi?.searchTrack(firstActivity.details.value + " " + firstActivity.state.value)

                    this.respond {
                        this.embed {
                            this.title = target.username + " listening to spotify"
                            this.thumbnail {
                                url = "https://i.scdn.co/image/" + firstActivity.assets.value?.largeImage?.value?.drop(8)
                            }
                            this.color = Color(30, 215, 96)
                            this.description = """
                                **[${firstActivity.details.value}](${track?.externalUrls?.externalUrls?.get("spotify")})**
                                by [`${firstActivity.state.value}`](${track?.artists?.firstOrNull { it.name.equals(firstActivity.state.value, true) }?.externalUrls?.externalUrls?.get("spotify")})
                                on [`${firstActivity.assets.value?.largeText?.value}`](${track?.album?.externalUrls?.externalUrls?.get("spotify")})
                            """.trimIndent()
                        }
                    }
                }
            }
        }
    }
}