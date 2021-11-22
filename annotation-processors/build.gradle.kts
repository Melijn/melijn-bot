plugins {
    kotlin("jvm") version "1.6.0"
}

group = "me.melijn.annotationprocessors"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.6.0-1.0.1")
}