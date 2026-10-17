package com.example.storyrig

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// Desktop preview only: `./gradlew :desktopApp:run --args="scene=p3 cache=0"`. No numbers come from here.
fun main(args: Array<String>) = application {
    val map = args.mapNotNull { a -> a.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    Window(
        onCloseRequest = ::exitApplication,
        title = "StoryRig perf spike",
        state = rememberWindowState(width = 1280.dp, height = 760.dp),
    ) {
        App(map)
    }
}
