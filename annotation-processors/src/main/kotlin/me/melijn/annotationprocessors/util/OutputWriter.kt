package me.melijn.annotationprocessors.util

import java.io.OutputStream

fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}