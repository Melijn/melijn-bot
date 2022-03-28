package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.model.AnimalType
import me.melijn.bot.utils.MISSING_IMAGE_URL
import me.melijn.bot.web.api.WebManager
import org.koin.core.component.inject

@KordExtension
class AnimalExtension : Extension() {

    override val name: String = "animal"
    private val webManager by inject<WebManager>()

    override suspend fun setup() {
        publicSlashCommand(::AnimalArg) {
            name = "animal"
            description = "shows many animals"

            action {
                val arg = this.arguments.animal.parsed
                respond {
                    animalEmbed(arg)
                }
            }
        }
    }

    inner class AnimalArg : Arguments() {
        val animal = enumChoice<AnimalType> {
            name = "animal"
            description = "animal to view"
            typeName = "animal"
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