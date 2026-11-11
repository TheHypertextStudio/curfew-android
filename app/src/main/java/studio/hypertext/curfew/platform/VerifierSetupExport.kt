package studio.hypertext.curfew.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MIME type passed to the Android document picker for verifier setup files. */
private const val SETUP_FILE_MIME_TYPE = "application/json"
/**
 * Suggested filename for a verifier setup file; the system picker lets the user change it.
 */
private const val SETUP_FILE_NAME = "curfew-check-in.json"
/**
 * ContentResolver mode that replaces a document's existing bytes rather than leaving trailing
 * data.
 */
private const val TRUNCATE_WRITE_MODE = "wt"

/**
 * Result of registering the verifier document picker. [pickerOpen] disables duplicate exports,
 * and [launch] starts the picker for a particular check-in ID.
 */
internal data class VerifierSetupExport(val pickerOpen: Boolean, val launch: (String) -> Unit)

/**
 * Registers the document picker at the check-in entry. Only the pending ID survives
 * recreation; the JSON and its secret are loaded after a document is selected and written on
 * an I/O dispatcher.
 */
@Composable
internal fun rememberVerifierSetupExport(
    onExport: (callbackId: String, write: suspend (String) -> Unit) -> Unit,
): VerifierSetupExport {
    val resolver = LocalContext.current.applicationContext.contentResolver
    // Retain only the selected check-in's identity across the system picker.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SETUP_FILE_MIME_TYPE)) { uri ->
        val id = pendingId
        pendingId = null
        if (id != null && uri != null) {
            onExport(id) { content ->
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri, TRUNCATE_WRITE_MODE)).bufferedWriter().use { it.write(content) }
                }
            }
        }
    }
    return VerifierSetupExport(pendingId != null) { id ->
        if (pendingId == null) {
            pendingId = id
            launcher.launch(SETUP_FILE_NAME)
        }
    }
}
