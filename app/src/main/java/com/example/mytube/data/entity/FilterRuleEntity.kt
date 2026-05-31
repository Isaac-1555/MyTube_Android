package com.example.mytube.data.entity

data class FilterRuleEntity(
    val pattern: String,
    val enabled: Boolean = true,
    val type: String = "host"
)
