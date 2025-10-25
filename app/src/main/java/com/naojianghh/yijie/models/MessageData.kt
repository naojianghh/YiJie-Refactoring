package com.naojianghh.yijie.models

enum class SenderType {
    AI,
    HUMAN
}

enum class ContentType {
    TEXT,
    IMAGE,
    LOADING
}

data class Message(
    val senderType: SenderType,
    val contentType: ContentType,
    val content: String ?= null,
    val sourceId: Int? = null,
    var isTyped: Boolean = false,

)
