package me.melijn.bot.events.leveling

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.Event

open class XPChangeEvent(api: JDA, open val oldXP: Long, open val newXP: Long, open val user: User) : Event(api) {

}