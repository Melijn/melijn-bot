package me.melijn.bot.model

import me.melijn.bot.model.enums.Alignment

data class Cell(
    val value: String,
    val alignment: Alignment = Alignment.LEFT
) {
    companion object {
        fun ofLeft(value: String) = Cell(value, Alignment.LEFT)
        fun ofCenter(value: String) = Cell(value, Alignment.CENTER)
        fun ofRight(value: String) = Cell(value, Alignment.RIGHT)
    }
}