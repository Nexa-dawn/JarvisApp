package com.jarvis.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvChat: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etInput: EditText
    private lateinit var btnMic: Button
    private lateinit var btnSend: Button
    private lateinit var scrollView: ScrollView

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var jarvisApi: JarvisApi
    private lateinit var commandProcessor: CommandProcessor

    private var isListening = false
    private var ttsReady = false

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            speak("Buyurun efendim")
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvChat = findViewById(R.id.tvChat)
        tvStatus = findViewById(R.id.tvStatus)
        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSend = findViewById(R.id.btnSend)
        scrollView = findViewById(R.id.scrollView)

        tts = TextToSpeech(this, this)
        jarvisApi = JarvisApi()
        commandProcessor = CommandProcessor(this, jarvisApi)

        checkPermissions()
        initSpeechRecognizer()
        startWakeWordService()

        btnMic.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                processInput(text)
                etInput.text.clear()
            }
        }

        addMessage("JARVIS", "Sistemler cevrimici. Merhaba efendim, JARVIS hazir.")
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(wakeReceiver, IntentFilter("com.jarvis.WAKE"), RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(wakeReceiver)
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("tr", "TR")
            tts.setSpeechRate(0.95f)
            tts.setPitch(0.85f)
            ttsReady = true
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                processInput(text)
                isListening = false
                tvStatus.text = "Hazir"
                btnMic.text = "MIC"
            }
            override fun onError(error: Int) {
                isListening = false
                tvStatus.text = "Hazir"
                btnMic.text = "MIC"
            }
            override fun onReadyForSpeech(p: Bundle?) { tvStatus.text = "Dinliyorum..." }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "Isleniyor..." }
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onRmsChanged(r: Float) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        }
        speechRecognizer.startListening(intent)
        isListening = true
        btnMic.text = "DUR"
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        tvStatus.text = "Hazir"
        btnMic.text = "MIC"
    }

    fun processInput(input: String) {
        addMessage("SIZ", input)
        tvStatus.text = "Dusunuyorum..."
        lifecycleScope.launch {
            try {
                val response = commandProcessor.process(input)
                addMessage("JARVIS", response)
                speak(response)
            } catch (e: Exception) {
                val err = "Uzgunum efendim, bir hata olustu."
                addMessage("JARVIS", err)
                speak(err)
            }
            tvStatus.text = "Hazir"
        }
    }

    fun speak(text: String) {
        if (ttsReady) {
            val clean = text.replace(Regex("\\[KOMUT:[^\\]]+\\]"), "").trim()
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun addMessage(sender: String, message: String) {
        runOnUiThread {
            val clean = message.replace(Regex("\\[KOMUT:[^\\]]+\\]"), "").trim()
            val cur = tvChat.text.toString()
            tvChat.text = if (cur.isEmpty()) "[$sender]: $clean" else "$cur\n\n[$sender]: $clean"
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        tts.shutdown()
    }
}
