package me.melijn.bot.music

import me.melijn.bot.utils.InferredChoiceEnum

enum class QueuePosition: InferredChoiceEnum {
    BOTTOM,
    RANDOM,
    TOP,
    TOP_SKIP;
}