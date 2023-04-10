package me.melijn.bot.events.leveling

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User

data class GuildXPChangeEvent(
    val api: JDA,
    override val oldXP: Long,
    override val newXP: Long,
    override val user: User,
    val guild: Guild
) : XPChangeEvent(api, oldXP, newXP, user) {
}