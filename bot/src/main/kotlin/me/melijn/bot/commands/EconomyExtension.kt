package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.numberChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.rest.builder.message.create.embed
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.BalanceManager
import me.melijn.bot.utils.KordExUtils.availableCurrency
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.gen.UserBalanceData
import org.koin.core.component.inject
import kotlin.random.Random

@KordExtension
class EconomyExtension : Extension() {

    override val name: String = "economy"

    val balanceManager by inject<BalanceManager>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "balance"
            description = "Shows your mel balance"

            action {
                val balance = balanceManager.get(this.user.id).balance
                respond {
                    embed {
                        description = tr("balance.show", balance)
                    }
                }
            }
        }
        publicSlashCommand(::PayArgs) {
            name = "pay"
            description = "send mel to another user"

            action {
                val target = arguments.target.parsed
                val amount = arguments.amount.parsed
                val balancePayer = balanceManager.get(user.id)
                val balanceReceiver = balanceManager.get(target.id)
                balancePayer.balance -= amount
                balanceReceiver.balance += amount
                balanceManager.store(balancePayer)
                balanceManager.store(balanceReceiver)
                respond {
                    content = tr(
                        "pay.payed",
                        target.mention, amount, balancePayer.balance
                    )
                }
            }
        }
        publicSlashCommand {
            name = "getmel"
            description = "Get an additional 100 mel"

            action {
                val balanceManager by inject<BalanceManager>()
                val balanceData = balanceManager.get(this.user.id)
                balanceData.balance += 100
                balanceManager.store(balanceData)
                respond {
                    embed {
                        description = "You now have **${balanceData.balance}** mel"
                    }
                }
            }
        }
        publicSlashCommand {
            name = "flip"
            description = "Flips a coin with chance to double the flipped amount"

            publicSubCommand(::FlipAllArgs) {
                name = "all"
                description = "all"

                action {
                    val side = arguments.coinSide.parsed

                    val balanceManager by inject<BalanceManager>()
                    val balanceData = balanceManager.get(user.id)
                    val amount = balanceData.balance
                    if (amount == 0L) {
                        respond {
                            content = translationsProvider.translate("flip.noMels")
                        }
                        return@action
                    }

                    flipAmount(side, balanceData, amount, balanceManager)
                }
            }

            publicSubCommand(::FlipAmountArgs) {
                name = "amount"
                description = "amount"

                action {
                    val amount = arguments.amount.parsed
                    val side = arguments.coinSide.parsed

                    val balanceData = balanceManager.get(user.id)

                    flipAmount(side, balanceData, amount, balanceManager)
                }
            }
        }

        publicSlashCommand {
            name = "beg"
            description = "Beg for a small amount of mel, max once every hour"

            action {
                // TODO: 1h cooldown
                // implement hourly beg cooldown
                val receivedAmount = Random.nextInt(11)
                val recipient = balanceManager.get(user.id)
                recipient.balance += receivedAmount
                balanceManager.store(recipient)
                respond {
                    content = tr("beg.receive",receivedAmount)
                }
            }
        }
    }

    private suspend fun PublicSlashCommandContext<*>.flipAmount(
        side: Long,
        balanceData: UserBalanceData,
        amount: Long,
        balanceManager: BalanceManager
    ) {
        // 0 represents Heads, 1 represents Tails
        val landed: Int
        val result: Int
        if (Random.nextBoolean()) {
            landed = side.toInt()
            balanceData.balance += amount
            result = 0
        } else {
            landed = if (side == 0L) 1 else 0
            balanceData.balance -= amount
            result = 1
        }

        balanceManager.store(balanceData)

        respond {
            embed {
                description = tr("flip.flipped", landed, result, amount, balanceData.balance)
            }
        }
    }

    inner class PayArgs : Arguments() {
        val target = user {
            name = "target"
            description = "user that will receive the mels"
            validate {
                failIf(translations.translate("pay.triedPayingSelf")) { value.id == context.getUser()?.id }
            }
        }
        val amount = availableCurrency("pay.triedPayingNothing", "pay.triedOverPaying") {
            name = "amount"
            description = "amount to pay"
        }
    }

    inner class FlipAmountArgs : Arguments() {
        val amount = availableCurrency("triedBettingNothing", "triedOverBetting") {
            name = "bet"
            description = "amount to bet"
        }
        val coinSide = coinSideArg()
    }

    open inner class FlipAllArgs : Arguments() {
        val coinSide = coinSideArg()
    }

    private fun Arguments.coinSideArg(): SingleConverter<Long> {
        return numberChoice {
            name = "side"
            description = "The side you want to bet on"
            choice("heads", 0)
            choice("tails", 1)
        }
    }
}