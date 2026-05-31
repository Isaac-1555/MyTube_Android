package com.example.mytube.data.entity

data class BookmarkEntity(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
