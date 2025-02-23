package com.example.smartvoiceassistant

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var recordButton: Button
    private lateinit var transcribedTextView: TextView
    private lateinit var todoRecyclerView: RecyclerView
    private lateinit var todoAdapter: TodoAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val REQUEST_PERMISSIONS = 200  // Single request code for all permissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.recordButton)
        transcribedTextView = findViewById(R.id.transcribedTextView)
        todoRecyclerView = findViewById(R.id.todoRecyclerView)

        todoAdapter = TodoAdapter(mutableListOf())
        todoRecyclerView.adapter = todoAdapter
        todoRecyclerView.layoutManager = LinearLayoutManager(this)

        recordButton.setOnClickListener {
            if (isListening) {
                stopSpeechRecognition()
            } else {
                startSpeechRecognition()
            }
        }

        requestPermissions() // Request all permissions at once

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
        } else {
            Toast.makeText(this, "Speech recognition is not available.", Toast.LENGTH_LONG).show()
            recordButton.isEnabled = false
        }
    }
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                    speechRecognizer?.setRecognitionListener(this)
                } else {
                    Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                    recordButton.isEnabled = false
                }
            } else {
                Toast.makeText(this, "Some permissions denied. App may not work correctly.", Toast.LENGTH_SHORT).show()
                // Handle permission denial gracefully (e.g., disable features)
                if (!permissions.contains(Manifest.permission.RECORD_AUDIO)){
                    recordButton.isEnabled = false
                }

            }
        }
    }

    private fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions() // Request permissions if not granted
            return
        }

        if (speechRecognizer == null) {
            Toast.makeText(this,"Speech recognition not initialized.",Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
        isListening = true
        transcribedTextView.text = ""
        transcribedTextView.hint = "Listening..."
    }

    private fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
        isListening = false
        transcribedTextView.hint = "Real-time transcription will appear here..."
    }
    override fun onReadyForSpeech(params: Bundle?) {
        Toast.makeText(this, "Ready For Speech", Toast.LENGTH_SHORT).show()
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { isListening = false; }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        Log.e("SpeechRecognizer", "Error: $errorMessage")
        Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_LONG).show()
        isListening = false
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            transcribedTextView.text = text
            todoAdapter.addTodo(TodoItem(text))
            tryToAddToCalendar(text) // Attempt to add to calendar
        }
        isListening = false;
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            transcribedTextView.text = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    private fun tryToAddToCalendar(text: String) {
        val (date, time, description) = extractDateTimeAndDescription(text)

        if (date != null) {
            addEventToCalendar(date, time, description)
        } else {
            Toast.makeText(this, "Could not find a valid date in the input.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractDateTimeAndDescription(text: String): Triple<Date?, String?, String> {
        val monthDayFormat = SimpleDateFormat("dd MMMM", Locale.US) // e.g., "25 March"
        val monthDayYearFormat = SimpleDateFormat("dd MMMM yyyy", Locale.US) // e.g., "25 March 2024"
        val dayMonthFormat = SimpleDateFormat("MMMM dd", Locale.US) // e.g., "March 25"
        val dayMonthYearFormat = SimpleDateFormat("MMMM dd yyyy", Locale.US) // e.g., "March 25 2024"

        monthDayFormat.isLenient = false
        monthDayYearFormat.isLenient = false
        dayMonthFormat.isLenient = false
        dayMonthYearFormat.isLenient = false


        val dateTimePattern = Pattern.compile(
            "\\b(?:(today|tomorrow|(?:\\d{1,2}(?:st|nd|rd|th)?\\s+)?(?:January|February|March|April|May|June|July|August|September|October|November|December)(?:\\s+\\d{1,2}(?:st|nd|rd|th)?)?(?:\\s+\\d{4})?|(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:\\s+\\d{4})?))\\b(?:\\s+(at\\s+)?(\\d{1,2}:\\d{2}\\s*(AM|PM)?))?",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = dateTimePattern.matcher(text)

        var date: Date? = null
        var time: String? = null
        var description = text

        if (matcher.find()) {
            var dateString = matcher.group(1) ?: ""
            val timeString = matcher.group(3)

            // Handle "today" and "tomorrow"
            if (dateString.equals("today", ignoreCase = true)) {
                date = Calendar.getInstance().time
            } else if (dateString.equals("tomorrow", ignoreCase = true)) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DATE, 1)
                date = cal.time
            } else {
                // Try parsing with different formats
                date = tryParseDate(dateString, monthDayYearFormat, monthDayFormat, dayMonthYearFormat, dayMonthFormat)
                //If date is not null, and no year was found, add the current year.
                if (date != null && !dateString.contains(Regex("\\d{4}"))) {
                    val cal = Calendar.getInstance()
                    cal.time = date
                    cal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    date = cal.time
                }
            }

            time = timeString?.trim()
            description = text.replace(matcher.group(0)!!, "").trim()
        }

        val timeWithoutAt = time?.replace(Regex("at\\s+", RegexOption.IGNORE_CASE), "")

        return Triple(date, timeWithoutAt, description)
    }


    private fun tryParseDate(dateString: String, vararg formats: SimpleDateFormat): Date? {
        for (format in formats) {
            try {
                return format.parse(dateString)
            } catch (e: ParseException) {
                // Continue to the next format
            }
        }
        return null
    }

    private fun addEventToCalendar(date: Date, time: String?, description: String) {
        val cal = Calendar.getInstance()
        cal.time = date

        if (time != null) {
            // Parse time, handling AM/PM
            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
            try {
                val parsedTime = timeFormat.parse(time)
                if (parsedTime != null) {
                    val timeCal = Calendar.getInstance()
                    timeCal.time = parsedTime
                    cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                }

            } catch (e: ParseException){
                //  Log.e("Calendar", "Invalid time format: $time", e) //For debugging
                //Don't set time, use the date only.
                Toast.makeText(this, "Invalid time format.  Using date only.", Toast.LENGTH_SHORT).show()
            }
        }


        val startMillis = cal.timeInMillis
        val endMillis = startMillis + (60 * 60 * 1000) // Add one hour

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, description)
            put(CalendarContract.Events.CALENDAR_ID, 1) // Default calendar (usually the primary)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.Calendar.getInstance().timeZone.id)
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            val uri: Uri? = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                Toast.makeText(this, "Event added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to add event", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Handle permission not granted (shouldn't happen, but good practice)
            Toast.makeText(this, "Calendar permission not granted", Toast.LENGTH_SHORT).show()
        }
    }
}