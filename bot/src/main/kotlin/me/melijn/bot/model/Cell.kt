package me.melijn.bot.model

import me.melijn.bot.model.enums.Alignment

data class Cell(
    val value: String,
    val alignment: Alignment = Alignment.LEFT
)