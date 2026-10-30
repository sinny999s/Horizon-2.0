// ShotBot: a tiny client mod that plays a test script (SHOT_SCRIPT) in the dev client and saves screenshots (SHOT_OUT).
// Build: ./gradlew build  ->  build/libs/shotbot.jar (copy it over shotbot-26.3.jar). Not part of the Horizon jar.
plugins {
    id("net.fabricmc.fabric-loom") version "1.19.1"
}

val mc = property("mc") as String
val api = if (mc == "26.2") "0.161.0+26.2" else "0.164.0+26.3"

base { archivesName = "shotbot" }

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:$api")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}
