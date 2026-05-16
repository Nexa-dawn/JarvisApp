package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class JarvisApi {
    companion object {
        private const val API_KEY = "AIzaSyAJO7JHABP5YXkZrkvFfbC_FJo3AIpVE2Y"
        private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        private const val SYSTEM = "Sen JARVIS'sin. Iron Man'deki gibi profesyonel ve sadik bir AI asistansin. Her zaman 'efendim' diyerek hitap et. Kisa ve net cevaplar ver. Turkce konus. Komut varsa cevabin sonuna ekle: [KOMUT:ALARM:08:30] [KOMUT:ARA:isim] [KOMUT:MESAJ:isim:mesaj] [KOMUT:MUZIK:OYNAT] [KOMUT:SES:ARTIR]"
    }

    private val history = mutableListOf<JSONObject>()

    suspend fun chat(message: String): String = withContext(Dispatchers.IO) {
        history.add(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", message)))
        })

        val contents = JSONArray()
        history.forEach { contents.put(it) }

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SYSTEM)))
            })
            put("contents", contents)
        }

        val conn = (URL("$API_URL?key=$API_KEY").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }

        val reply = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .getJSONObject(0).getString("text")

        history.add(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", reply)))
        })
        if (history.size > 20) history.removeAt(0)

        reply
    }
}
