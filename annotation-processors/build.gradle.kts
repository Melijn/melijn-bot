plugins {
    kotlin("jvm") version "1.6.10"
}

group = "me.melijn.annotationprocessors"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.6.10-1.0.2")
}