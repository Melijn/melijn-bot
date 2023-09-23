package me.melijn.bot.commands.thirdparty

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.messages.InlineMessage
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.MISSING_IMAGE_URL
import me.melijn.bot.utils.embedWithColor
import me.melijn.bot.web.api.WebManager
import net.dv8tion.jda.api.utils.messages.MessageCreateData
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

    context(CommandContext, InlineMessage<MessageCreateData>)
    private suspend fun animalEmbed(animal: AnimalType) {
        embedWithColor {
            title = animal.ucc()
            image = webManager.animalImageApi.getRandomAnimalImage(animal) ?: MISSING_IMAGE_URL
        }
    }
}

enum class AnimalSource {
    ImgHoard,
    RandomDuk,
    Duncte123,
    SomeRandomApi
}

enum class AnimalType(
    vararg val pairs: Pair<AnimalSource, String>
) : InferredChoiceEnum {
    CAT(AnimalSource.ImgHoard to "cat"),
    DOG(AnimalSource.ImgHoard to "dog"),
    BIRD(AnimalSource.SomeRandomApi to "bird"),
    FISH(AnimalSource.ImgHoard to "fish"),
    CHICKEN(AnimalSource.ImgHoard to "chicken"),
    ALPACA(AnimalSource.Duncte123 to "alpaca"),
    DUCK(AnimalSource.RandomDuk to ""),
    FOX(AnimalSource.SomeRandomApi to "fox"),
    KOALA(AnimalSource.SomeRandomApi to "koala"),
    PANDA(AnimalSource.SomeRandomApi to "panda"),
    FROG(AnimalSource.ImgHoard to "frog"),
    LYNX(AnimalSource.ImgHoard to "lynx"),
    NYAN_CAT(AnimalSource.ImgHoard to "nyancat"),
    PENGUIN(AnimalSource.ImgHoard to "penguin"),
    POSSUM(AnimalSource.ImgHoard to "possum"),
    SNEK(AnimalSource.ImgHoard to "snake"),
    YOSHI(AnimalSource.ImgHoard to "yoshi"),
}