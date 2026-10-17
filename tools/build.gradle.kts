// Asset tools (JVM only, javax.imageio + java.awt). Not part of the app.
plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.example.storyrig.tools.KaiCutKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
