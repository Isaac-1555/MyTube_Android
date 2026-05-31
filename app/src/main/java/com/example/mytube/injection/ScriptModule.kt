package com.example.mytube.injection

data class ScriptModule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val injectionTime: InjectionTime = InjectionTime.AT_DOCUMENT_END,
    val source: String
)

enum class InjectionTime {
    AT_DOCUMENT_START,
    AT_DOCUMENT_END
}
