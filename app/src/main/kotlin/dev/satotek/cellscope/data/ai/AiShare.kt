package dev.satotek.cellscope.data.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.snapshot.SnapshotWriter
import java.io.File

object AiShare {
    /** With [attachment] the CSV rides along as EXTRA_STREAM; the digest stays in EXTRA_TEXT for receivers that show both. */
    fun share(context: Context, digest: String, attachment: File? = null) {
        copy(context, digest)
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, digest)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.ai_digest_title))
            if (attachment != null) {
                val uri = FileProvider.getUriForFile(context, SnapshotWriter.AUTHORITY, attachment)
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, attachment.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ai_digest_title), text))
    }
}
