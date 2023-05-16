package me.melijn.botannotationprocessors.uselimit

annotation class UseLimit(val tableType: TableType) {
    enum class TableType {
        HISTORY,
        COOLDOWN,
        LIMIT_HIT
    }
}
