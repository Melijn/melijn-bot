package me.melijn.bot.model

import me.melijn.bot.music.QueuePosition
import me.melijn.bot.music.Track

class SearchPlayMenu(
    val options: Array<Track>,
    val queuePosition: QueuePosition
)