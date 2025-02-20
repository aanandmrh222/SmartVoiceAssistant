package com.example.smartvoiceassistant.models

import java.util.Date

data class Task(
    val description: String,
    val dueDate: Date? = null,
    val isKeyPoint: Boolean = false
)