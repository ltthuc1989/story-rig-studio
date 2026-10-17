package com.example.storyrig

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform