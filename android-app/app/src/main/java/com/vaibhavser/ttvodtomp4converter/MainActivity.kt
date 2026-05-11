package com.vaibhavser.ttvodtomp4converter

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.vaibhavser.ttvodtomp4converter.ui.theme.TTVodToMp4ConverterTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConverterUiState(
    val selectedUri: Uri? = null,
    val selectedLabel: String = "No file selected",
    val isConverting: Boolean = false,
    val progressPercent: Int = 0,
    val statusMessage: String = "Pick a .vod or video file to create an MP4 export.",
    val outputPath: String? = null,
    val errorMessage: String? = null,
)

class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf(ConverterUiState())
    private val progressHandler = Handler(Looper.getMainLooper())

    private var transformer: Transformer? = null
    private var latestOutputFile: File? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            val activeTransformer = transformer ?: return
            val progressHolder = ProgressHolder()
            val progressState = activeTransformer.getProgress(progressHolder)

            if (uiState.value.isConverting) {
                val progress = if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progressHolder.progress.coerceIn(0, 100)
                } else {
                    uiState.value.progressPercent
                }

                val progressText = when (progressState) {
                    Transformer.PROGRESS_STATE_AVAILABLE -> {
                        "Converting on device... $progress%"
                    }

                    Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY -> {
                        "Preparing the export pipeline..."
                    }

                    else -> {
                        "Transcoding on device..."
                    }
                }

                uiState.value = uiState.value.copy(
                    progressPercent = progress,
                    statusMessage = progressText,
                )
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers do not grant persistable permissions. The session permission is enough.
        }

        val displayName = queryDisplayName(uri) ?: "Selected video"
        uiState.value = ConverterUiState(
            selectedUri = uri,
            selectedLabel = displayName,
            statusMessage = "Ready to export $displayName as MP4.",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TTVodToMp4ConverterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConverterScreen(
                        state = uiState.value,
                        onPickFile = { openPicker() },
                        onStartConversion = { startConversion() },
                        onCancelConversion = { cancelConversion() },
                        onShareOutput = { shareLatestOutput() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        progressHandler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        transformer = null
        super.onDestroy()
    }

    private fun openPicker() {
        pickFileLauncher.launch(arrayOf("*/*", "video/*"))
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun startConversion() {
        val sourceUri = uiState.value.selectedUri ?: run {
            Toast.makeText(this, "Choose a .vod file first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (uiState.value.isConverting) {
            return
        }

        val outputFile = createOutputFile()
        latestOutputFile = outputFile

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                progressHandler.removeCallbacks(progressRunnable)
                transformer = null
                uiState.value = uiState.value.copy(
                    isConverting = false,
                    progressPercent = 100,
                    statusMessage = "MP4 export finished.",
                    outputPath = outputFile.absolutePath,
                    errorMessage = null,
                )
            }

            override fun onError(
                composition: Composition,
                result: ExportResult,
                exception: ExportException,
            ) {
                progressHandler.removeCallbacks(progressRunnable)
                transformer = null
                uiState.value = uiState.value.copy(
                    isConverting = false,
                    statusMessage = "Conversion failed.",
                    errorMessage = exception.localizedMessage ?: "Unknown export error.",
                )
            }
        }

        val localTransformer = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()

        transformer = localTransformer
        uiState.value = uiState.value.copy(
            isConverting = true,
            progressPercent = 0,
            statusMessage = "Preparing the MP4 export...",
            outputPath = null,
            errorMessage = null,
        )

        localTransformer.start(MediaItem.fromUri(sourceUri), outputFile.absolutePath)
        progressHandler.post(progressRunnable)
    }

    private fun cancelConversion() {
        transformer?.cancel()
        transformer = null
        progressHandler.removeCallbacks(progressRunnable)
        uiState.value = uiState.value.copy(
            isConverting = false,
            progressPercent = 0,
            statusMessage = "Conversion cancelled.",
            errorMessage = null,
        )
    }

    private fun shareLatestOutput() {
        val outputFile = latestOutputFile
        if (outputFile == null || !outputFile.exists()) {
            Toast.makeText(this, "No MP4 is ready to share yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val shareUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            outputFile,
        )

        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, shareUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(Intent.createChooser(shareIntent, "Share MP4"))
    }

    private fun createOutputFile(): File {
        val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val exportDir = File(moviesDir, "tt-vod-exports").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(exportDir, "tt_vod_export_$timestamp.mp4")
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
        cursor.useSafe { safeCursor ->
            if (safeCursor != null && safeCursor.moveToFirst()) {
                val columnIndex = safeCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    return safeCursor.getString(columnIndex)
                }
            }
        }
        return uri.lastPathSegment
    }
}

private inline fun <T> Cursor?.useSafe(block: (Cursor?) -> T): T {
    return this.use(block)
}

@Composable
private fun ConverterScreen(
    state: ConverterUiState,
    onPickFile: () -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onShareOutput: () -> Unit,
) {
    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF6F1E8),
            Color(0xFFE8F0F7),
            Color(0xFFDDE7D8),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard()

            StatusCard(state = state)

            ActionCard(
                state = state,
                onPickFile = onPickFile,
                onStartConversion = onStartConversion,
                onCancelConversion = onCancelConversion,
                onShareOutput = onShareOutput,
            )

            InfoCard()
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF17324D),
            contentColor = Color(0xFFF8FAFC),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "TT VOD To MP4 Converter",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Pick a .vod file, export a clean MP4 on-device, and share the result straight from Android.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD8E7F4),
            )
        }
    }
}

@Composable
private fun StatusCard(state: ConverterUiState) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Selected file",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.selectedLabel,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.isConverting) {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )
            }
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF425466),
            )
            if (state.outputPath != null) {
                Text(
                    text = "Output: ${state.outputPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2A6A45),
                )
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A1C1C),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    state: ConverterUiState,
    onPickFile: () -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onShareOutput: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F8FB)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose file")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStartConversion,
                    enabled = state.selectedUri != null && !state.isConverting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Convert to MP4")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelConversion,
                    enabled = state.isConverting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onShareOutput,
                    enabled = state.outputPath != null && !state.isConverting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share MP4")
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF6EA)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Quality notes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This Android build uses Media3 Transformer for reliable on-device MP4 output with H.264 video and AAC audio.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "The repository also includes a desktop Python converter that keeps the optional AI enhancement flow.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
