package com.buddy.app.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buddy.app.*
import com.buddy.app.ai.BuddyAI
import com.buddy.app.camera.CameraHelper
import com.buddy.app.databinding.ActivityMainBinding
import com.buddy.app.memory.MemoryRepository
import com.buddy.app.service.BuddyLiveService
import com.buddy.app.service.BootReceiver
import com.buddy.app.voice.VoiceEngine
import com.buddy.app.voice.WakeWordService
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var voice: VoiceEngine
    private lateinit var ai: BuddyAI
    private lateinit var adapter: MessageAdapter
    private lateinit var memory: MemoryRepository
    private lateinit var camera: CameraHelper

    private var processing = false
    private var camActive  = false
    private var capturedB64: String? = null

    // ── Receivers ──────────────────────────────────────────────────────────

    /** Wake word: start push-to-talk */
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            if (i.action == WakeWordService.ACTION_DETECTED) activateMic()
        }
    }

    /** Proactive update from BuddyLiveService → show in chat + speak */
    private val proactiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            if (i.action == BuddyLiveService.ACTION_PROACTIVE) {
                val msg = i.getStringExtra(BuddyLiveService.EXTRA_MSG) ?: return
                val cat = i.getStringExtra(BuddyLiveService.EXTRA_CATEGORY) ?: ""
                runOnUiThread { showProactive(msg, cat) }
            }
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.RECORD_AUDIO] == true) startServices()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        memory = (application as BuddyApplication).memory
        camera = CameraHelper(this)
        initAI()
        initVoice()
        initUI()
        requestPerms()
        checkSetup()
        lifecycleScope.launch { memory.incrementSessions() }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter()
        f.addAction(WakeWordService.ACTION_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeReceiver, f, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeReceiver, f)
        }

        val f2 = IntentFilter(BuddyLiveService.ACTION_PROACTIVE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(proactiveReceiver, f2, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(proactiveReceiver, f2)
        }

        // Refresh API key in case user updated settings
        val key = prefs().getString("api_key", "") ?: ""
        ai.updateKey(key)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(wakeReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(proactiveReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.destroy()
        camera.destroy()
    }

    // ── Init helpers ───────────────────────────────────────────────────────

    private fun initAI() {
        val key = prefs().getString("api_key", "") ?: ""
        ai = BuddyAI(memory, key)
    }

    private fun initVoice() {
        voice = VoiceEngine(this)

        voice.onSpeakStart = {
            b.waveform.setState(WaveformView.State.SPEAKING)
            setStatus("Buddy is speaking…")
            b.btnMic.alpha = 0.35f
            b.btnMic.isEnabled = false
        }

        voice.onSpeakDone = {
            // After Buddy finishes speaking → idle, ready for next tap
            b.waveform.setState(WaveformView.State.IDLE)
            setStatus("Hold mic to speak")
            b.btnMic.alpha = 1f
            b.btnMic.isEnabled = true
        }

        voice.onListenStart = {
            b.waveform.setState(WaveformView.State.LISTENING)
            setStatus("Listening…")
        }

        voice.onListenDone = {
            if (!processing) {
                b.waveform.setState(WaveformView.State.IDLE)
                setStatus("Hold mic to speak")
            }
        }

        voice.onResult = { text ->
            b.etInput.setText("")
            b.etInput.hint = "Type a message…"
            sendMessage(text, capturedB64)
            capturedB64 = null
        }

        voice.onPartial = { partial ->
            b.etInput.hint = partial
        }
    }

    private fun initUI() {
        adapter = MessageAdapter()
        b.rvMessages.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
        }
        b.waveform.setState(WaveformView.State.IDLE)

        // ── Push-to-talk mic ──────────────────────────────────────────────
        b.btnMic.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { activateMic(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { deactivateMic(); true }
                else -> false
            }
        }

        // ── Send (text) ───────────────────────────────────────────────────
        b.btnSend.setOnClickListener {
            val txt = b.etInput.text.toString().trim()
            if (txt.isNotBlank()) {
                b.etInput.setText("")
                sendMessage(txt, capturedB64)
                capturedB64 = null
            }
        }

        b.etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                b.btnSend.isEnabled = s?.isNotBlank() == true
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
        })

        // ── Camera ────────────────────────────────────────────────────────
        b.btnCamera.setOnClickListener { toggleCamera() }
        b.btnCapture.setOnClickListener { capturePhoto() }

        // ── Settings ──────────────────────────────────────────────────────
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── Long-press waveform → clear chat ──────────────────────────────
        b.waveform.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear chat?")
                .setMessage("Clears conversation. Memory is kept.")
                .setPositiveButton("Clear") { _, _ -> adapter.clear(); ai.clearHistory() }
                .setNegativeButton("Cancel", null).show()
            true
        }
    }

    // ── Mic: push-to-talk ──────────────────────────────────────────────────

    private fun activateMic() {
        if (processing || voice.isSpeaking()) return
        vibrate(40)
        voice.startListening()
    }

    private fun deactivateMic() {
        voice.stopListening()
    }

    // ── Send message ───────────────────────────────────────────────────────

    private fun sendMessage(text: String, img: String? = null) {
        if (processing) return
        processing = true

        adapter.add(BuddyMessage(text = text, isUser = true))
        scrollDown()

        b.waveform.setState(WaveformView.State.THINKING)
        setStatus("Thinking…")
        b.btnMic.alpha = 0.35f
        b.btnMic.isEnabled = false
        b.btnSend.isEnabled = false

        val key = prefs().getString("api_key", "") ?: ""
        ai.updateKey(key)

        lifecycleScope.launch {
            val reply = withContext(Dispatchers.IO) { ai.chat(text, img) }
            withContext(Dispatchers.Main) {
                processing = false
                b.btnSend.isEnabled = true
                addBuddyMsg(reply, "")
                voice.speak(reply, voice.detectLang(text))
            }
        }
    }

    /** Called when BuddyLiveService pushes a proactive update */
    private fun showProactive(msg: String, category: String) {
        if (processing) return   // Don't interrupt if user is actively chatting
        addBuddyMsg(msg, category)
        voice.speak(msg)
    }

    private fun addBuddyMsg(text: String, category: String = "") {
        adapter.add(BuddyMessage(text = text, isUser = false, category = category))
        scrollDown()
    }

    private fun scrollDown() {
        b.rvMessages.post {
            if (adapter.itemCount > 0)
                b.rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun setStatus(s: String) { b.tvStatus.text = s }

    // ── Camera ─────────────────────────────────────────────────────────────

    private fun toggleCamera() {
        if (camActive) {
            b.cameraPreview.visibility = View.GONE
            b.btnCapture.visibility    = View.GONE
            camera.stopCamera(); camActive = false
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                b.cameraPreview.visibility = View.VISIBLE
                b.btnCapture.visibility    = View.VISIBLE
                camActive = true
                camera.startCamera(this, b.cameraPreview) {}
            } else {
                Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun capturePhoto() {
        camera.capturePhoto(
            onCaptured = { b64 ->
                runOnUiThread {
                    capturedB64 = b64
                    sendMessage("Buddy, look at this carefully and describe what you see in detail.", b64)
                    capturedB64 = null
                    toggleCamera()
                }
            },
            onError = { runOnUiThread { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() } }
        )
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun checkSetup() {
        val key = prefs().getString("api_key", "") ?: ""
        if (key.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("One thing needed.")
                .setMessage("I need an Anthropic API key to think. Get one free at console.anthropic.com")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Later", null).show()
        }
    }

    // ── Permissions ────────────────────────────────────────────────────────

    private fun requestPerms() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startServices() else permLauncher.launch(missing.toTypedArray())
    }

    private fun startServices() {
        fun startFg(cls: Class<*>) {
            val i = Intent(this, cls)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        }
        if (!WakeWordService.isRunning) startFg(WakeWordService::class.java)
        if (!BuddyLiveService.isRunning) startFg(BuddyLiveService::class.java)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun vibrate(ms: Long) {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    private fun prefs() = getSharedPreferences("buddy_prefs", MODE_PRIVATE)
}
