package dev.schoolgrades

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File

/** Saving a downloaded report to the Downloads folder, or handing it to another app. */
object PdfExport {

    fun safeName(displayName: String): String {
        val cleaned = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Rapport" }
        return if (cleaned.endsWith(".pdf", ignoreCase = true)) cleaned else "$cleaned.pdf"
    }

    /** Android 10+ only (no storage permission needed). Returns false if unsupported or it failed. */
    fun saveToDownloads(context: Context, file: File, displayName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            saveWithMediaStore(context, file, displayName)
        } else {
            false
        }
    }

    @RequiresApi(29)
    private fun saveWithMediaStore(context: Context, file: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName(displayName))
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: error("No output stream")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    fun share(context: Context, file: File, displayName: String) {
        // Copy to a nicely named file so the receiving app shows a readable name.
        val named = File(file.parentFile, safeName(displayName))
        if (!named.exists() || (named.length() != file.length())) file.copyTo(named, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", named)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
