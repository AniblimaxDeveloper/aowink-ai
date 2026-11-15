package com.aowink.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class Message(val id: String = UUID.randomUUID().toString(), val role: String, val content: String)

class AowinkViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun sendMessage(text: String, apiKey: String) {
        if (text.isBlank() || _isLoading.value) return
        if (apiKey.isBlank()) {
            _messages.value = _messages.value + Message(role = "assistant", content = "⚠️ API Key kosong. Klik ikon gerigi di atas untuk mengisi.")
            return
        }

        _messages.value = _messages.value + Message(role = "user", content = text)
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                _messages.value.takeLast(15).forEach { msg ->
                    jsonArray.put(JSONObject().apply { put("role", msg.role); put("content", msg.content) })
                }

                val body = JSONObject().apply {
                    put("model", "nvidia/nemotron-3-ultra-550b-a55b:free") // Model stabil
                    put("messages", jsonArray)
                    put("stream", true)
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val source = response.body?.source()
                var aiText = ""
                val aiMsg = Message(role = "assistant", content = "")
                _messages.value = _messages.value + aiMsg

                if (source != null) {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        if (line != null && line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ")
                            if (data == "[DONE]") break
                            try {
                                val delta = JSONObject(data).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content") ?: ""
                                if (content.isNotEmpty()) {
                                    aiText += content
                                    val updated = _messages.value.toMutableList()
                                    updated[updated.size - 1] = aiMsg.copy(content = aiText)
                                    _messages.value = updated
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + Message(role = "assistant", content = "❌ Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
