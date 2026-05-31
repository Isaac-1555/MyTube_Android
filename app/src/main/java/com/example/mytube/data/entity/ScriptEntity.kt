package com.example.mytube.data.entity

data class ScriptEntity(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val source: String,
    val injectionTime: String = "atDocumentEnd"
)
