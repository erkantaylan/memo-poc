package com.erkantaylan.kitaplik.open

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.erkantaylan.kitaplik.catalog.LibraryItem
import java.io.File

/**
 * Hands a downloaded file to whichever app the user already uses for it.
 *
 * PDFs are deliberately not rendered in-app: a dedicated reader does it better,
 * and none of the reading modes could use a PDF anyway.
 */
object ExternalOpener {

    sealed interface Result {
        data object Opened : Result
        data class NoHandler(val mimeType: String) : Result
    }

    fun open(context: Context, item: LibraryItem, file: File): Result {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.kind.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            Result.Opened
        } catch (_: ActivityNotFoundException) {
            Result.NoHandler(item.kind.mimeType)
        }
    }
}
