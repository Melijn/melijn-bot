rootProject.name = "Melijn-Bot"
include("bot")
include("site")
include("site-annotation-processors")
sourceControl {
    gitRepository(java.net.URI.create("https://github.com/ToxicMushroom/Lavalink.kt.git")) {
        producesModule("dev.schlaubi.lavakord:kord")
    }
}