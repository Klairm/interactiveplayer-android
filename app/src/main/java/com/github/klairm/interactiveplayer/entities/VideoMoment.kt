package com.github.klairm.interactiveplayer.entities

data class VideoMoment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val type: String,
    val bodyText: String? = null,
    val choices: List<Choice>? = null
)

