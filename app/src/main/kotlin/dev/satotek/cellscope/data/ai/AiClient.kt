package dev.satotek.cellscope.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AiClient(private val context: Context) {
    private companion object { const val MAX_CSV_CHARS = 150_000 }

    suspend fun ask(digest: String, attachment: File? = null): Result<String> = withContext(Dispatchers.IO) {
        val cfg = AiPrefs.load(context)
        if (!cfg.hasKey) return@withContext Result.failure(IOException("no API key"))
        val prompt = if (attachment == null) digest else digest + "\n\n## CSV (" + attachment.name + ")\n```csv\n" + csvTail(attachment) + "\n```"
        runCatching {
            when (cfg.provider) {
                AiProvider.OPENAI -> openai(cfg, prompt)
                AiProvider.GEMINI -> gemini(cfg, prompt)
                AiProvider.ANTHROPIC -> anthropic(cfg, prompt)
            }
        }
    }

    /** Header line plus the newest rows that fit in [MAX_CSV_CHARS] — enough for an hour at 1 s without blowing the context. */
    private fun csvTail(f: File): String {
        val text = f.readText()
        if (text.length <= MAX_CSV_CHARS) return text.trimEnd()
        val header = text.lineSequence().firstOrNull().orEmpty()
        val tail = text.substring(text.length - MAX_CSV_CHARS).substringAfter('\n')
        return header + "\n…\n" + tail.trimEnd()
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
