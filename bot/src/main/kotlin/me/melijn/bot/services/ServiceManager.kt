package me.melijn.bot.services

class ServiceManager {
    init {
        SpotifyAuthRefreshService().start()
    }
}