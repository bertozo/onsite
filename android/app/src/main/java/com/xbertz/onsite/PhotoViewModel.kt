package com.xbertz.onsite

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.photo.PhotoStamper
import com.xbertz.onsite.photo.TimestampPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoUiState(
    val position: TimestampPosition = TimestampPosition.BOTTOM,
    /** Absolute path of the logo file, or null when no logo is set (photos are then saved without one). */
    val logoPath: String? = null,
    /** Gallery Uri of the last stamped photo, for the preview and the share button. */
    val lastPhotoUri: Uri? = null,
    val isProcessing: Boolean = false,
    @StringRes val errorRes: Int? = null
)

/**
 * Site-photo tool: takes a picture through the camera app, stamps date/time (+ optional logo)
 * on it and saves it to the gallery. Settings persist in the "settings" SharedPreferences and
 * the logo as a file in filesDir.
 */
class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val logoFile = File(application.filesDir, LOGO_FILE)

    private val _uiState = MutableStateFlow(
        PhotoUiState(
            position = runCatching { TimestampPosition.valueOf(prefs.getString(KEY_POSITION, null) ?: "") }
                .getOrDefault(TimestampPosition.BOTTOM),
            logoPath = logoFile.takeIf { it.exists() }?.absolutePath
        )
    )
    val uiState: StateFlow<PhotoUiState> = _uiState

    /** The file the camera app is currently writing to; consumed by [onCaptureResult]. */
    private var pendingCapture: File? = null

    fun setPosition(position: TimestampPosition) {
        prefs.edit().putString(KEY_POSITION, position.name).apply()
        _uiState.update { it.copy(position = position) }
    }

    fun onLogoPicked(uri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        logoFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("no stream")
                }.isSuccess
            }
            if (saved) _uiState.update { it.copy(logoPath = logoFile.absolutePath) }
        }
    }

    fun clearLogo() {
        logoFile.delete()
        _uiState.update { it.copy(logoPath = null) }
    }

    /** Creates the temp file the camera app will fill and returns its FileProvider Uri. */
    fun newCaptureUri(): Uri {
        val application = getApplication<Application>()
        val dir = File(application.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        pendingCapture = file
        _uiState.update { it.copy(errorRes = null) }
        return FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
    }

    fun onCaptureResult(success: Boolean) {
        val file = pendingCapture ?: return
        pendingCapture = null
        if (!success) {
            file.delete()
            return
        }
        val takenAt = LocalDateTime.now()
        val state = _uiState.value
        _uiState.update { it.copy(isProcessing = true, errorRes = null) }
        viewModelScope.launch {
            val application = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sourceUri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
                    val bitmap = PhotoStamper.stamp(
                        application, sourceUri, takenAt.format(STAMP_FORMAT), state.position, state.logoPath
                    )
                    try {
                        PhotoStamper.saveToGallery(application, bitmap, "OnSite_${takenAt.format(FILE_FORMAT)}.jpg")
                    } finally {
                        bitmap.recycle()
                    }
                }.also { file.delete() }.onFailure { Log.e(TAG, "Stamping photo failed", it) }
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    lastPhotoUri = result.getOrNull() ?: it.lastPhotoUri,
                    errorRes = if (result.isFailure) R.string.photo_error_save else null
                )
            }
        }
    }

    private companion object {
        const val TAG = "PhotoViewModel"
        const val KEY_POSITION = "photo_timestamp_position"
        const val LOGO_FILE = "stamp_logo.png"
        const val CAPTURE_DIR = "photos"
        val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH)
        val FILE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH)
    }
}
