package com.buddy.app.voice

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.buddy.app.BuddyApplication
import com.buddy.app.R
import com.buddy.app.ui.MainActivity
import kotlinx.coroutines.*

class WakeWordService : Service() {
    companion object {
        const val ACTION_DETECTED = "com.buddy.app.WAKE_WORD"
        var isRunning = false
    }

    private var stt: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var restartJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(101, buildNotif())
        initRecognizer()
    }

    private fun buildNotif() = NotificationCompat.Builder(this, BuddyApplication.CH_WAKE)
        .setSmallIcon(R.drawable.ic_buddy)
        .setContentTitle("Buddy")
        .setContentText("Listening for \"Hey Buddy\"")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_MIN).build()

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        stt = SpeechRecognizer.createSpeechRecognizer(this)
        stt?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches?.any { it.lowercase().contains("hey buddy") } == true) triggerWake()
                else scheduleRestart(800)
            }
            override fun onPartialResults(r: Bundle?) {
                val partial = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (partial.lowercase().contains("hey buddy")) triggerWake()
            }
            override fun onError(e: Int) { scheduleRestart(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2000L else 1200L) }
            override fun onEndOfSpeech() {}
            override fun onBeginningOfSpeech() {}
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        startRecognition()
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { stt?.startListening(intent) } catch (e: Exception) { scheduleRestart(1000) }
    }

    private fun triggerWake() {
        sendBroadcast(Intent(ACTION_DETECTED).setPackage(packageName))
        scheduleRestart(5000)
    }

    private fun scheduleRestart(ms: Long) {
        restartJob?.cancel()
        restartJob = scope.launch { delay(ms); if (isRunning) startRecognition() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        isRunning = false
        restartJob?.cancel(); scope.cancel(); stt?.destroy()
        super.onDestroy()
    }
}
