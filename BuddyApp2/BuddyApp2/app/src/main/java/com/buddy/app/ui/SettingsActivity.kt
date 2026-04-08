package com.buddy.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.buddy.app.BuddyApplication
import com.buddy.app.databinding.ActivitySettingsBinding
import com.buddy.app.memory.MemoryRepository
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var memory: MemoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        memory = (application as BuddyApplication).memory
        load()
        b.btnSave.setOnClickListener { save() }
        b.btnBack.setOnClickListener { finish() }
    }

    private fun load() {
        CoroutineScope(Dispatchers.Main).launch {
            b.etApiKey.setText(prefs().getString("api_key", ""))
            b.etName.setText(memory.get(MemoryRepository.NAME) ?: "")
            b.etAge.setText(memory.get(MemoryRepository.AGE) ?: "")
            b.etOccupation.setText(memory.get(MemoryRepository.OCCUPATION) ?: "")
            b.etCity.setText(memory.get(MemoryRepository.CITY) ?: "")
            b.etInterests.setText(memory.get(MemoryRepository.INTERESTS) ?: "")
        }
    }

    private fun save() {
        val key = b.etApiKey.text.toString().trim()
        if (key.isBlank()) { Toast.makeText(this, "API key required", Toast.LENGTH_SHORT).show(); return }
        prefs().edit().putString("api_key", key).apply()
        CoroutineScope(Dispatchers.IO).launch {
            val name = b.etName.text.toString().trim()
            val age  = b.etAge.text.toString().trim()
            val occ  = b.etOccupation.text.toString().trim()
            val city = b.etCity.text.toString().trim()
            val int  = b.etInterests.text.toString().trim()
            if (name.isNotBlank()) memory.set(MemoryRepository.NAME, name)
            if (age.isNotBlank())  memory.set(MemoryRepository.AGE, age)
            if (occ.isNotBlank())  memory.set(MemoryRepository.OCCUPATION, occ)
            if (city.isNotBlank()) memory.set(MemoryRepository.CITY, city)
            if (int.isNotBlank())  memory.set(MemoryRepository.INTERESTS, int)
        }
        Toast.makeText(this, "Saved. Buddy's ready.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun prefs() = getSharedPreferences("buddy_prefs", MODE_PRIVATE)
}
