package com.studylock.tv.model

data class Question(
    val id: String,
    val grade: Int, // 1..9
    val gradeLabel: String, // e.g. "一年级"
    val subject: String, // "语文" | "数学" | "英语" | "拼音题"
    val volume: String, // "上册" | "下册" | "通用"
    val prompt: String,
    val promptPinyin: String? = null,
    val options: List<String>,
    val optionsPinyin: List<String>? = null,
    val correctIndex: Int, // 0..3
    val explanation: String
)
