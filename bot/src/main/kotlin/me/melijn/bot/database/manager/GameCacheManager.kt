package me.melijn.bot.database.manager

import me.melijn.gen.database.manager.AbstractTicTacToeManager
import me.melijn.gen.database.manager.AbstractTicTacToePlayerManager
import me.melijn.kordkommons.database.DriverManager

class TicTacToeGameManager(driverManager: DriverManager) : AbstractTicTacToeManager(driverManager) {

}

class TicTacToePlayerManager(driverManager: DriverManager) : AbstractTicTacToePlayerManager(driverManager) {

}