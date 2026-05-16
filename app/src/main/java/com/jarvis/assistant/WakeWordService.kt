package com.jarvis.assistant

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WakeWordService : Service() {
    private lateinit var recognizer: SpeechRecognizer
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle) {
                val text = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                if (text.contains("jarvis")) {
                    sendBroadcast(Intent("com.jarvis.WAKE").apply { setPackage(packageName) })
                }
                restart()
            }
            override fun onError(e: Int) { restart(1000) }
            override fun onReadyForSpeech(p: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle) {}
            override fun onPartialResults(p: Bundle) {
                val text = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                if (text.contains("jarvis")) {
                    recognizer.stopListening()
                    sendBroadcast(Intent("com.jarvis.WAKE").apply { setPackage(packageName) })
                    restart(2000)
                }
            }
            override fun onRmsChanged(r: Float) {}
        })
        listen()
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try { recognizer.startListening(intent) } catch (e: Exception) { restart() }
    }

    private fun restart(delay: Long = 500) {
        scope.launch { delay(delay); listen() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("jarvis", "JARVIS", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, "jarvis")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("JARVIS")
        .setContentText("Dinliyorum...")
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); scope.cancel(); recognizer.destroy() }
}
