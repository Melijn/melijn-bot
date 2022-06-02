package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.model.AnimalType
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.MISSING_IMAGE_URL
import me.melijn.bot.utils.embedWithColor
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
                val arg = arguments.animal.parsed
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

    context(CommandContext, MessageCreateBuilder)
    private suspend fun animalEmbed(animal: AnimalType) {
        embedWithColor(Color(100, 100, 220)) {
            title = animal.ucc()
            image = webManager.animalImageApi.getRandomAnimalImage(animal) ?: MISSING_IMAGE_URL
        }
    }
}