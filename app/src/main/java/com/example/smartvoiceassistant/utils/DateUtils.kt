package com.example.smartvoiceassistant.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    fun parseDateTime(text: String): Date? {
        val formats = listOf(
            "MM/dd/yyyy HH:mm", "MM/dd/yyyy", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "MMM dd, yyyy HH:mm", "MMM dd, yyyy", "HH:mm", "h:mm a"
        )

        formats.forEach { formatString ->
            try {
                val sdf = SimpleDateFormat(formatString, Locale.US)
                val date = sdf.parse(text)
                if (date != null) return date
            } catch (e: ParseException) {
                // Ignore
            }
        }

        val calendar = Calendar.getInstance()
        when {
            text.contains("today", ignoreCase = true) -> return calendar.time
            text.contains("tomorrow", ignoreCase = true) -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return calendar.time
            }
            text.contains("next week", ignoreCase = true) -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                return calendar.time
            }
        }

        val daysOfWeek = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        for ((index, day) in daysOfWeek.withIndex()) {
            if (text.contains(day, ignoreCase = true)) {
                val dayOfWeek = (index + 1) % 7 + 1
                while (calendar.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                return calendar.time
            }
        }
        return null
    }

    fun formatDateForDisplay(date: Date): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
        return sdf.format(date)
    }
}
