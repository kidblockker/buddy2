package com.buddy.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class VoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var stt: SpeechRecognizer? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())

    // ── Callbacks ──────────────────────────────────────────────────────────
    var onSpeakStart:  (() -> Unit)? = null
    var onSpeakDone:   (() -> Unit)? = null
    var onListenStart: (() -> Unit)? = null
    var onListenDone:  (() -> Unit)? = null
    var onResult:      ((String) -> Unit)? = null
    var onPartial:     ((String) -> Unit)? = null

    init { initTTS(); initSTT() }

    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language    = Locale.US
                tts?.setPitch(0.72f)      // deep voice
                tts?.setSpeechRate(0.85f) // calm pace
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?)  = handler.post { onSpeakStart?.invoke() }.let {}
                    override fun onDone(id: String?)   = handler.post { onSpeakDone?.invoke() }.let {}
                    override fun onError(id: String?)  = handler.post { onSpeakDone?.invoke() }.let {}
                })
            }
        }
    }

    private fun initSTT() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        stt = SpeechRecognizer.createSpeechRecognizer(context)
        stt?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) = handler.post { onListenStart?.invoke() }.let {}
            override fun onEndOfSpeech()              = handler.post { onListenDone?.invoke() }.let {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                handler.post { if (text.isNotBlank()) onResult?.invoke(text) else onListenDone?.invoke() }
            }
            override fun onPartialResults(p: Bundle?) {
                val text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) handler.post { onPartial?.invoke(text) }
            }
            override fun onError(error: Int) = handler.post { onListenDone?.invoke() }.let {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
            override fun onRmsChanged(r: Float) {}
        })
    }

    /** Speak text aloud. After done → onSpeakDone fires, caller decides what to do next */
    fun speak(text: String, lang: String = "en") {
        if (!ttsReady) return
        tts?.language = when {
            lang.startsWith("hi") -> Locale("hi","IN")
            lang.startsWith("bn") -> Locale("bn","IN")
            else -> Locale.US
        }
        stopListening()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() { tts?.stop() }

    /** Start one-shot listening session — fires onResult once then stops */
    fun startListening(lang: String = "en-IN") {
        if (isSpeaking()) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        try { stt?.startListening(intent) } catch (e: Exception) { onListenDone?.invoke() }
    }

    fun stopListening() { try { stt?.stopListening() } catch (e: Exception) {} }
    fun isSpeaking() = tts?.isSpeaking == true

    fun detectLang(text: String) = when {
        text.any { it.code in 0x0900..0x097F } -> "hi"
        text.any { it.code in 0x0980..0x09FF } -> "bn"
        else -> "en"
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop(); tts?.shutdown(); stt?.destroy()
    }
}
