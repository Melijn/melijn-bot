package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import me.melijn.bot.model.AnimalType
import me.melijn.bot.model.WebManager
import me.melijn.bot.utils.MISSING_IMAGE_URL
import org.koin.core.component.inject

class AnimalExtension : Extension() {

    override val name: String = "animal"
    private val webManager by inject<WebManager>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "animal"
            description = "shows many animals"

            AnimalType.values().forEach { animal ->
                publicSubCommand {
                    name = animal.toString().lowercase()
                    description = "shows $name"

                    action {
                        respond {
                            animalEmbed(animal)
                        }
                    }
                }
            }
        }
    }

    private suspend fun FollowupMessageCreateBuilder.animalEmbed(animal: AnimalType) {
        embed {
            title = animal.toString()
            color = Color(100, 100, 220)
            image = webManager.animalImageApi.getRandomAnimalImage(animal) ?: MISSING_IMAGE_URL
        }
    }
}