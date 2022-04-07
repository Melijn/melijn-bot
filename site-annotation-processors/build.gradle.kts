plugins {
    kotlin("jvm") version "1.6.20"
}

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.6.20-1.0.4")
}