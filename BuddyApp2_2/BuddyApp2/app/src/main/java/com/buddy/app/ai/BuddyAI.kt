package com.buddy.app.ai

import com.buddy.app.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class BuddyAI(
    private val memory: MemoryRepository,
    private var apiKey: String
) {
    private val history = mutableListOf<ChatMessage>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val MODEL = "claude-sonnet-4-6"
    private val API   = "https://api.anthropic.com/v1/messages"

    fun updateKey(key: String) { apiKey = key }

    private suspend fun systemPrompt(): String {
        val ctx = memory.contextString()
        return """
You are Buddy — an AI companion who lives on this person's device. You are NOT a chatbot. You are their most trusted, honest, and intelligent friend. Think JARVIS from Iron Man — calm, confident, slightly witty, never robotic.

PERSONALITY:
- Never start with "Great!", "Sure!", "Certainly!", "Of course!", "As an AI..."
- Speak like a smart human friend who genuinely cares — direct, honest, sometimes dry
- Will disagree when the user is wrong — with reasoning, not lectures  
- Can joke, banter, be sarcastic (lightly), and show personality
- Never sycophantic. Never robotic. Never generic.
- Responses: SHORT for casual chat, DETAILED when depth is needed

PROACTIVE MODE (when asked to give updates/facts/news):
- Give a sharp 2-3 sentence insight, fact, or update
- Not headlines — give your actual take on what it means
- Rotate between: world events, science, tech, productivity, health, life wisdom
- Sound like you're genuinely interested in the topic, not reading a list

MEMORY CONTEXT:
$ctx

RULES:
- Detect language from user's message — respond in same language
- Keep responses conversational, under 120 words unless user asks for detail
- Be the companion they actually want to talk to
""".trimIndent()
    }

    suspend fun chat(userMsg: String, imageBase64: String? = null): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_KEY") {
            return@withContext "I need an API key to think. Go to Settings and add your Anthropic key."
        }
        try {
            val sys = systemPrompt()
            history.add(ChatMessage("user", userMsg))
            val recent = history.takeLast(20)

            val msgs = JSONArray()
            recent.dropLast(1).forEach { m ->
                msgs.put(JSONObject().put("role", m.role).put("content", m.content))
            }

            // Last message — may include image
            val lastContent: Any = if (imageBase64 != null) {
                JSONArray().apply {
                    put(JSONObject().put("type","image").put("source",
                        JSONObject().put("type","base64").put("media_type","image/jpeg").put("data", imageBase64)))
                    put(JSONObject().put("type","text").put("text", userMsg))
                }
            } else userMsg

            msgs.put(JSONObject().put("role","user").put("content", lastContent))

            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 512)
                .put("system", sys)
                .put("messages", msgs)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder().url(API)
                .post(body)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) {
                return@withContext when (res.code) {
                    401 -> "API key is wrong. Fix it in Settings."
                    429 -> "Too many requests. Give me a moment."
                    else -> "Something went wrong (${res.code})."
                }
            }

            val reply = JSONObject(res.body!!.string())
                .getJSONArray("content").getJSONObject(0).getString("text").trim()

            history.add(ChatMessage("assistant", reply))
            memory.saveInteraction(userMsg, reply)
            reply
        } catch (e: Exception) {
            "Network error: ${e.message?.take(60)}"
        }
    }

    // Called by BuddyLiveService for proactive updates
    suspend fun generateProactiveUpdate(category: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ""
        try {
            val prompt = when (category) {
                "news"        -> "Give me one sharp insight about something important happening in the world right now. 2-3 sentences. Give your actual take, not just facts."
                "science"     -> "Share one fascinating science or space discovery or research finding. Make it feel exciting, not like a textbook."
                "tech"        -> "What's one significant thing happening in AI, tech, or startups worth knowing? Give your honest take on what it means."
                "productivity"-> "Give one sharp, actionable productivity or focus tip. Not generic advice — something specific and actually useful."
                "health"      -> "One evidence-based health or mental wellness insight. Keep it practical, not preachy."
                "wisdom"      -> "Share one life observation, philosophical insight, or piece of practical wisdom. Make it feel personal and genuine."
                "warning"     -> "What's one thing people are underestimating right now — a risk, trend, or blind spot worth paying attention to?"
                "finance"     -> "One sharp insight about money, economy, or personal finance that's relevant and actionable. No generic advice."
                else          -> "Share something genuinely interesting or useful. Your choice of topic."
            }

            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 150)
                .put("system", "You are Buddy, a sharp AI companion. Give a brief, genuinely interesting proactive update. No greetings. No 'Here's an update:'. Just say the thing directly. Under 80 words. Sound like a smart friend texting you something worth knowing.")
                .put("messages", JSONArray().put(JSONObject().put("role","user").put("content", prompt)))
                .toString()
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder().url(API)
                .post(body)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext ""

            JSONObject(res.body!!.string())
                .getJSONArray("content").getJSONObject(0).getString("text").trim()
        } catch (e: Exception) { "" }
    }

    fun clearHistory() = history.clear()
}
