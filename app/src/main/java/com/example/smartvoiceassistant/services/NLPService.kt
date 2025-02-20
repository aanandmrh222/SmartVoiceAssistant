package com.example.smartvoiceassistant.services

import com.example.smartvoiceassistant.models.Task
import com.example.smartvoiceassistant.utils.DateUtils
import java.util.Date

class NLPService {
    fun extractActions(text: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val lines = text.split(".") // Simple sentence splitting

        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {

                val actionKeywords = listOf("need to", "should", "must", "task", "action item", "follow up", "we will")
                val isAction = actionKeywords.any { trimmedLine.lowercase().contains(it) }

                val dateTime: Date? = DateUtils.parseDateTime(trimmedLine)

                val keyPointKeywords = listOf("important", "key point", "remember", "note that")
                val isKeyPoint = keyPointKeywords.any { trimmedLine.lowercase().contains(it) }

                if (isAction || dateTime != null || isKeyPoint) {
                    tasks.add(Task(trimmedLine, dateTime, isKeyPoint))
                }
            }
        }
        return tasks
    }
}