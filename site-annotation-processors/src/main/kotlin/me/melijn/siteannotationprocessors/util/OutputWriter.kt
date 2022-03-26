package me.melijn.siteannotationprocessors.util

import java.io.OutputStream

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}

fun OutputStream.appendLine(str: String) {
    this.write(str.toByteArray())
    this.write('\n'.code)
}