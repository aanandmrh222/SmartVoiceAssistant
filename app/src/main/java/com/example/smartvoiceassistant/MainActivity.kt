package com.example.smartvoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartvoiceassistant.models.Task
import com.example.smartvoiceassistant.services.CalendarService
import com.example.smartvoiceassistant.services.NLPService
import com.example.smartvoiceassistant.services.SpeechRecognitionService
import com.example.smartvoiceassistant.utils.DateUtils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), RecognitionListener {

    companion object {
        private const val REQUEST_PERMISSIONS = 100 // Single request code
        private const val TAG = "MainActivity"
    }

    private lateinit var textViewTranscript: TextView
    private lateinit var textViewStatus: TextView
    private lateinit var fabToggleRecording: FloatingActionButton // Changed to FAB
    private lateinit var listViewActions: ListView
    private lateinit var progressBar: ProgressBar // Added ProgressBar
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private lateinit var nlpService: NLPService
    private lateinit var calendarService: CalendarService
    private lateinit var actionsAdapter: ActionAdapter
    private val extractedTasks = mutableListOf<Task>()
    private var fullTranscript = "" // Store the full transcript

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewTranscript = findViewById(R.id.textViewTranscript)
        textViewStatus = findViewById(R.id.textViewStatus)
        fabToggleRecording = findViewById(R.id.fabToggleRecording) // Find the FAB
        listViewActions = findViewById(R.id.listViewActions)
        progressBar = findViewById(R.id.progressBar) // Find the ProgressBar

        nlpService = NLPService()
        calendarService = CalendarService(this)

        actionsAdapter = ActionAdapter()
        listViewActions.adapter = actionsAdapter

        requestPermissions() // Request both permissions

        createSpeechRecognizer()

        fabToggleRecording.setOnClickListener { // FAB click listener
            if (isRecording) stopRecording() else startRecording()
        }
    }

    private fun createSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(this)
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_CALENDAR)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant audio recording permission", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            createSpeechRecognizer()
        }

        fullTranscript = "" // Reset transcript
        textViewTranscript.text = "" // Clear previous text

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        speechRecognizer?.startListening(intent)
        isRecording = true
        fabToggleRecording.setImageResource(R.drawable.ic_stop) // Change icon to stop
        textViewStatus.text = "Status: Recording..."
        progressBar.visibility = View.VISIBLE // Show progress bar
    }

    private fun stopRecording() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecording = false
        fabToggleRecording.setImageResource(R.drawable.ic_mic) // Change icon to mic
        textViewStatus.text = "Status: Stopped"
        progressBar.visibility = View.GONE

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                // All permissions granted, proceed
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
            } else {
                // Some permissions denied.  You should inform the user.
                Toast.makeText(this, "Some Permissions Denied", Toast.LENGTH_SHORT).show()
                // Consider disabling features that require the denied permissions.
            }
        }
    }


    override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "Ready for speech") }
    override fun onBeginningOfSpeech() { Log.d(TAG, "Beginning of speech"); textViewStatus.text = "Status: Listening..." }
    override fun onRmsChanged(rmsdB: Float) {  }
    override fun onBufferReceived(buffer: ByteArray?) {  }
    override fun onEndOfSpeech() {
        Log.d(TAG, "End of speech detected by system") // Log this, but don't stop
        // Don't stop recording here!
    }
    override fun onError(error: Int) {
        Log.e(TAG, "Error: $error")
        textViewStatus.text = "Error: ${SpeechRecognitionService.getErrorText(error)}"
        stopRecording()
    }

    override fun onResults(results: Bundle) {
        Log.d(TAG, "Results received")
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            fullTranscript = matches.joinToString(" ") // Use the stored fullTranscript
            textViewTranscript.text = fullTranscript

            lifecycleScope.launch {
                val extractedActions = withContext(Dispatchers.Default) {
                    nlpService.extractActions(fullTranscript)
                }
                extractedTasks.clear()
                extractedTasks.addAll(extractedActions)

                extractedTasks.forEach { task ->
                    task.dueDate?.let {
                        calendarService.addEvent("Meeting", task.description, it.time)
                    }
                }
                actionsAdapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE // Hide progress bar after processing.
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!partialMatches.isNullOrEmpty()) {
            val partialText = partialMatches.joinToString(" ")
            fullTranscript += partialText // Append partial results
            textViewTranscript.text = fullTranscript //update the view
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        TODO("Not yet implemented")
    }


    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    inner class ActionAdapter : BaseAdapter() {
        override fun getCount(): Int = extractedTasks.size
        override fun getItem(position: Int): Task = extractedTasks[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val viewHolder: ViewHolder

            if (convertView == null) {
                view = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_action, parent, false)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else {
                view = convertView
                viewHolder = view.tag as ViewHolder
            }

            val task = getItem(position)
            viewHolder.descriptionTextView.text = task.description

            if (task.dueDate != null) {
                viewHolder.dueDateTextView.text = DateUtils.formatDateForDisplay(task.dueDate)
                viewHolder.dueDateTextView.visibility = View.VISIBLE
            } else {
                viewHolder.dueDateTextView.visibility = View.GONE
            }

            return view
        }
    }

    private class ViewHolder(view: View) {
        val descriptionTextView: TextView = view.findViewById(R.id.textViewActionDescription)
        val dueDateTextView: TextView = view.findViewById(R.id.textViewActionDueDate)
    }
}