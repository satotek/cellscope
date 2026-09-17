package dev.satotek.cellscope.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AiClient(private val context: Context) {
    suspend fun ask(digest: String): Result<String> = withContext(Dispatchers.IO) {
        val cfg = AiPrefs.load(context)
        if (!cfg.hasKey) return@withContext Result.failure(IOException("no API key"))
        runCatching {
            when (cfg.provider) {
                AiProvider.OPENAI -> openai(cfg, digest)
                AiProvider.GEMINI -> gemini(cfg, digest)
                AiProvider.ANTHROPIC -> anthropic(cfg, digest)
            }
        }
    }

    private fun openai(cfg: AiConfig, digest: String): String {
        val body = JSONObject()
            .put("model", cfg.resolvedModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", digest)))
            .toString()
        val (code, text) = post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${cfg.apiKey}"),
            body,
        )
        if (code !in 200..299) throw httpError(code, text)
        return JSONObject(text)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
            .ifBlank { throw IOException("empty response") }
    }

    private fun gemini(cfg: AiConfig, digest: String): String {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", digest))),
                ),
            )
            .toString()
        val model = URLEncoder.encode(cfg.resolvedModel, StandardCharsets.UTF_8)
        val key = URLEncoder.encode(cfg.apiKey, StandardCharsets.UTF_8)
        val (code, text) = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key",
            emptyMap(),
            body,
        )
        if (code !in 200..299) throw httpError(code, text)
        return JSONObject(text)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
            .ifBlank { throw IOException("empty response") }
    }

    private fun anthropic(cfg: AiConfig, digest: String): String {
        val body = JSONObject()
            .put("model", cfg.resolvedModel)
            .put("max_tokens", 2048)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", digest)))
            .toString()
        val (code, text) = post(
            "https://api.anthropic.com/v1/messages",
            mapOf(
                "x-api-key" to cfg.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            body,
        )
        if (code !in 200..299) throw httpError(code, text)
        return JSONObject(text)
            .getJSONArray("content").getJSONObject(0)
            .getString("text").trim()
            .ifBlank { throw IOException("empty response") }
    }

    private fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun httpError(code: Int, body: String): IOException {
        val snippet = body.replace('\n', ' ').trim().take(200)
        return IOException("HTTP $code\n$snippet")
    }
}
