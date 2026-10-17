import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.storyrig.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.storyrig"
            packageVersion = "1.0.0"
        }
    }
}
// Off-screen snapshots: ./gradlew :desktopApp:snapshot --args="out=<dir> scene=kai"
tasks.register<JavaExec>("snapshot") {
    group = "application"
    mainClass = "com.example.storyrig.SnapshotKt"
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("java.awt.headless", "true")
}
