package com.buddy.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import com.buddy.app.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var b: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.waveform.setState(WaveformView.State.THINKING)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2200)
    }
}
