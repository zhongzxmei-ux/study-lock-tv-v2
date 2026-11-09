package com.studylock.tv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.studylock.tv.config.LockConfig
import com.studylock.tv.model.Question
import java.io.File

class QuestionRepository(private val context: Context) {
    private var cachedQuestions: List<Question>? = null

    private fun loadAll(): List<Question> {
        cachedQuestions?.let { return it }
        val list = mutableListOf<Question>()
        val gson = Gson()
        val type = object : TypeToken<List<Question>>() {}.type

        // 1. 加载内置人教版/统编版全年级题库
        try {
            val json = context.assets.open("questions_sample.json")
                .bufferedReader().use { it.readText() }
            val builtIn: List<Question>? = gson.fromJson(json, type)
            if (builtIn != null) list.addAll(builtIn)
        } catch (_: Exception) {
        }

        // 2. 支持 U 盘/小米电视「下载」目录导入扩展人教版 JSON 题库包
        //    例如从开源项目 xiaoyang111222/cn-math-taxonomy 转换后的 JSON
        try {
            val externalFile = File("/sdcard/Download/study_lock_questions.json")
            if (externalFile.exists() && externalFile.canRead()) {
                val extJson = externalFile.readText(Charsets.UTF_8)
                val extList: List<Question>? = gson.fromJson(extJson, type)
                if (extList != null) list.addAll(extList)
            }
        } catch (_: Exception) {
        }

        cachedQuestions = list
        return list
    }

    fun reload() {
        cachedQuestions = null
    }

    fun getTotalCount(): Int = loadAll().size

    fun pickRandomQuestion(config: LockConfig, excludeIds: Set<String> = emptySet()): Question? {
        val all = loadAll()
        val subjects = config.enabledSubjects
        val grades = config.enabledGrades.mapNotNull { it.toIntOrNull() }.toSet()

        val filtered = all.filter { q ->
            (subjects.isEmpty() || q.subject in subjects || (q.subject == "拼音题" && "语文" in subjects)) &&
                (grades.isEmpty() || q.grade in grades) &&
                q.id !in excludeIds
        }

        val pool = filtered.ifEmpty {
            all.filter { it.id !in excludeIds }.ifEmpty { all }
        }
        return pool.randomOrNull()
    }
}
