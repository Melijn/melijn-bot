package me.melijn.bot.events.leveling

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member

data class GuildXPChangeEvent(
    override val oldXP: Long,
    override val newXP: Long,
    val member: Member
) : XPChangeEvent(oldXP, newXP, member.user) {
    val guild: Guild = member.guild
    val jda = getJDA()
}