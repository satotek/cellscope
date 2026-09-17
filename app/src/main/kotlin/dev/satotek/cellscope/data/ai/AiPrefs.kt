package dev.satotek.cellscope.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.satotek.cellscope.R

enum class AiProvider(val id: String, val defaultModel: String) {
    OPENAI("openai", "gpt-5.6-luna"),
    GEMINI("gemini", "gemini-3.8-flash"),
    ANTHROPIC("anthropic", "claude-sonnet-5");

    companion object {
        fun of(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

data class AiConfig(
    val provider: AiProvider = AiProvider.OPENAI,
    val apiKey: String = "",
    val model: String = "",
    val prompt: String = "",
) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
    val resolvedModel: String get() = model.ifBlank { provider.defaultModel }
    fun resolvedPrompt(context: Context): String = prompt.ifBlank { context.getString(R.string.ai_prompt) }
}

object AiPrefs {
    private const val FILE = "ai_secrets"
    private const val K_PROVIDER = "provider"
    private const val K_KEY = "api_key"
    private const val K_MODEL = "model"
    private const val K_PROMPT = "prompt"

    fun load(context: Context): AiConfig {
        val p = prefs(context) ?: return AiConfig()
        return AiConfig(
            provider = AiProvider.of(p.getString(K_PROVIDER, null)),
            apiKey = p.getString(K_KEY, "") ?: "",
            model = p.getString(K_MODEL, "") ?: "",
            prompt = p.getString(K_PROMPT, "") ?: "",
        )
    }

    fun save(context: Context, config: AiConfig) {
        val p = prefs(context) ?: return
        p.edit()
            .putString(K_PROVIDER, config.provider.id)
            .putString(K_KEY, config.apiKey)
            .putString(K_MODEL, config.model)
            .putString(K_PROMPT, config.prompt)
            .apply()
    }

    fun hasKey(context: Context): Boolean = load(context).hasKey

    @Suppress("DEPRECATION")
    private fun prefs(context: Context): SharedPreferences? = runCatching {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            FILE,
            alias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        runCatching {
            context.deleteSharedPreferences(FILE)
            val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE,
                alias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }
}
