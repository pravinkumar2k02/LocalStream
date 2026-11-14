plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

allprojects {
    layout.buildDirectory.set(file("C:/Users/p.k.palanisamy/AppData/Local/Temp/DesktopStreamPlayer_build/${project.name}"))
}

group = "com.localstream.player"
version = "1.0.0"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls", "javafx.media", "javafx.web")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation("uk.co.caprica:vlcj-javafx:1.2.1")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.localstream.player.LocalStreamApp")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
