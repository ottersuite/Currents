package app.otter.client.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What came of a save, in the words the snackbar should use. */
sealed interface MediaSaveResult {
    data class Saved(val album: String) : MediaSaveResult
    data class Failed(val reason: String) : MediaSaveResult
}

/**
 * Writes a viewed asset into the device's own gallery.
 *
 * The file goes to the shared media store rather than to the app's private storage, so it turns
 * up in the gallery app next to everything else the phone has taken — which is the only reason
 * to save one at all. On Android 10 and later that needs no permission; below it there is no
 * shared store to write through and the file is placed in the public folder by hand, which does.
 */
object MediaSaver {
    private const val ALBUM = "Currents"

    /** The name the file gets. Times, because two saves from one post are otherwise one file. */
    private fun fileName(extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "currents_$stamp.$extension"
    }

    /**
     * True when saving [asset] needs the legacy storage permission first.
     *
     * Only Android 9 and below: from 10 the media store hands out a URI the app may write
     * without asking for anything.
     */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /**
     * True when there is a file behind [asset] to write down at all.
     *
     * The action sheet asks first rather than offering a save that can only fail: an adaptive
     * stream has no single file to keep.
     */
    fun canSave(asset: MediaAsset): Boolean = downloadableSource(asset) != null

    suspend fun save(context: Context, asset: MediaAsset): MediaSaveResult =
        withContext(Dispatchers.IO) {
            val source = downloadableSource(asset)
                ?: return@withContext MediaSaveResult.Failed(
                    "Reddit only streams this clip in pieces, so there is no file to save",
                )
            val type = MediaType.of(source, asset.kind)
            runCatching { write(context, source, type) }
                .fold(
                    onSuccess = { MediaSaveResult.Saved("${type.album}/$ALBUM") },
                    onFailure = { MediaSaveResult.Failed(it.message ?: "Could not save that") },
                )
        }

    /**
     * The source worth keeping on disk.
     *
     * An adaptive manifest is a list of segments, not a file: saving one leaves a playlist that
     * points at URLs which expire. So a manifest is skipped in favour of any progressive source
     * the asset also carries — which for Reddit's own video is the MP4 it generates alongside.
     */
    private fun downloadableSource(asset: MediaAsset): String? =
        listOfNotNull(asset.url, asset.fallbackUrl).firstOrNull { candidate ->
            val path = candidate.substringBefore('?').lowercase()
            !path.endsWith(".m3u8") && !path.endsWith(".mpd")
        }

    private fun write(context: Context, url: String, type: MediaType): String {
        val request = Request.Builder().url(url).build()
        OtterHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
            val body = response.body
            val name = fileName(type.extension)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToMediaStore(context, name, type) { output ->
                    body.byteStream().use { it.copyTo(output) }
                }
            } else {
                writeToPublicFolder(context, name, type) { output ->
                    body.byteStream().use { it.copyTo(output) }
                }
            }
            return name
        }
    }

    private fun writeToMediaStore(
        context: Context,
        name: String,
        type: MediaType,
        write: (java.io.OutputStream) -> Unit,
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, type.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${type.album}/$ALBUM")
            // Hidden from the gallery until the bytes are all there, so a half-written file is
            // never the thumbnail someone taps on.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(type.collection, values)
            ?: throw IOException("The gallery would not accept a new file")
        try {
            resolver.openOutputStream(target)?.use(write)
                ?: throw IOException("The gallery would not open that file for writing")
        } catch (error: Throwable) {
            resolver.delete(target, null, null)
            throw error
        }
        resolver.update(
            target,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private fun writeToPublicFolder(
        context: Context,
        name: String,
        type: MediaType,
        write: (java.io.OutputStream) -> Unit,
    ) {
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(type.legacyFolder), ALBUM)
        if (!folder.exists() && !folder.mkdirs()) {
            throw IOException("Could not create the $ALBUM folder")
        }
        val file = File(folder, name)
        try {
            file.outputStream().use(write)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        // Nothing watches this folder on these versions; the gallery only learns about the file
        // when it is told.
        MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(type.mimeType), null)
    }

    /** Where a saved file belongs, and what to call it. */
    private data class MediaType(
        val collection: android.net.Uri,
        val album: String,
        val legacyFolder: String,
        val mimeType: String,
        val extension: String,
    ) {
        companion object {
            fun of(url: String, kind: MediaKind): MediaType {
                val path = url.substringBefore('?').lowercase()
                val extension = path.substringAfterLast('/')
                    .substringAfterLast('.', "")
                    .takeIf { it.length in 2..4 }
                    .orEmpty()
                return when {
                    // Reddit serves the MP4 re-encoding of a GIF from the same `.gif` path,
                    // distinguished only by the query. Saved under the path's own extension it
                    // would be an MP4 in a file every gallery app tries to decode as a GIF.
                    url.contains("format=mp4", ignoreCase = true) -> video("video/mp4", "mp4")
                    extension == "gif" -> image("image/gif", "gif")
                    extension in IMAGE_EXTENSIONS -> image(
                        mimeType = "image/${if (extension == "jpg") "jpeg" else extension}",
                        extension = extension,
                    )
                    extension in VIDEO_EXTENSIONS -> video("video/$extension", extension)
                    kind == MediaKind.IMAGE -> image("image/jpeg", "jpg")
                    else -> video("video/mp4", "mp4")
                }
            }

            private fun image(mimeType: String, extension: String) = MediaType(
                collection = imagesCollection(),
                album = Environment.DIRECTORY_PICTURES,
                legacyFolder = Environment.DIRECTORY_PICTURES,
                mimeType = mimeType,
                extension = extension,
            )

            private fun video(mimeType: String, extension: String) = MediaType(
                collection = videosCollection(),
                album = Environment.DIRECTORY_MOVIES,
                legacyFolder = Environment.DIRECTORY_MOVIES,
                mimeType = mimeType,
                extension = extension,
            )

            private fun imagesCollection() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

            private fun videosCollection() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

            private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp")
            private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv")
        }
    }
}
