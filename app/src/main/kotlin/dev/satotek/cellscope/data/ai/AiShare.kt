package dev.satotek.cellscope.data.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import dev.satotek.cellscope.R

object AiShare {
    fun share(context: Context, digest: String) {
        copy(context, digest)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, digest)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.ai_digest_title))
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ai_digest_title), text))
    }
}
