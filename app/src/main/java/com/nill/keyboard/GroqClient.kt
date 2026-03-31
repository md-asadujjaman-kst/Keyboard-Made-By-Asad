package com.nill.keyboard

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val BASE = "https://api.groq.com/openai/v1"

    fun banglish(text: String, cb: (String) -> Unit) {
        val sys = "You convert Banglish (Bengali written in English letters) to proper Bengali Unicode. Reply ONLY with the converted Bengali text, nothing else.\nExamples:\namar naam Shahed -> আমার নাম শাহেদ\nami bhalo achi -> আমি ভালো আছি\nami coding korte pocondo kori -> আমি কোডিং করতে পছন্দ করি"
        val body = JSONObject()
        body.put("model", "llama-3.1-8b-instant")
        body.put("max_tokens", 500)
        body.put("temperature", 0.1)
        val msgs = JSONArray()
        val sm = JSONObject(); sm.put("role", "system"); sm.put("content", sys); msgs.put(sm)
        val um = JSONObject(); um.put("role", "user"); um.put("content", text); msgs.put(um)
        body.put("messages", msgs)

        val req = Request.Builder()
            .url("$BASE/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cb(text) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = JSONObject(response.body?.string() ?: "")
                    val result = json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim()
                    cb(result)
                } catch (e: Exception) { cb(text) }
            }
        })
    }

    fun transcribe(file: File, lang: String, cb: (String) -> Unit) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/m4a".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", lang)
            .addFormDataPart("response_format", "json")
            .build()

        val req = Request.Builder()
            .url("$BASE/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cb("") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    cb(JSONObject(response.body?.string() ?: "").optString("text", "").trim())
                } catch (e: Exception) { cb("") }
            }
        })
    }
}
