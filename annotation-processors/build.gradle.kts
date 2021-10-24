plugins {
    kotlin("jvm") version "1.5.31"
}

group = "me.melijn.annotationprocessors"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.5.31-1.0.0")
}