package app.otter.client.ui.components.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.otter.client.data.MediaSaver
import app.otter.client.model.MediaAsset

/**
 * A save that asks for storage permission first, but only where one is still needed.
 *
 * From Android 10 the media store hands out a writable URI to any app, so the common path never
 * sees a dialog at all. Below that there is no such store, the file has to be written into the
 * public folder by hand, and that does need asking — once, the first time someone saves.
 */
@Composable
fun rememberMediaSaveRequest(
    onSave: (MediaAsset) -> Unit,
    onDenied: () -> Unit,
): (MediaAsset) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<MediaAsset?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val asset = pending
        pending = null
        when {
            !granted -> onDenied()
            asset != null -> onSave(asset)
        }
    }

    return { asset ->
        val granted = !MediaSaver.needsLegacyPermission() ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onSave(asset)
        } else {
            pending = asset
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
