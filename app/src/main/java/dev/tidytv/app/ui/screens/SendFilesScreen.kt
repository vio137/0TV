package dev.tidytv.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tidytv.app.FilesState
import dev.tidytv.app.PickedFile
import dev.tidytv.app.SendItem
import dev.tidytv.app.SendStatus
import dev.tidytv.app.ui.BigButton
import dev.tidytv.app.ui.IconBadge
import dev.tidytv.app.ui.InfoChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.ui.text.style.TextAlign
import dev.tidytv.app.ui.Body
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.Note
import dev.tidytv.app.ui.Outline
import dev.tidytv.app.ui.ProgressBar
import dev.tidytv.app.ui.SafetyBanner
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.Title
import dev.tidytv.app.ui.TopBar
import java.util.Locale

/** Send files from the phone to the TV. */
@Composable
fun SendFilesScreen(
    s: FilesState,
    tvName: String?,
    onPicked: (List<PickedFile>) -> Unit,
    onCancel: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onBack: () -> Unit,
    onFolder: (dev.tidytv.core.ops.SendFolder) -> Unit = {},
) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                var name = "file"
                var size = 0L
                runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val z = c.getColumnIndex(OpenableColumns.SIZE)
                            if (n >= 0 && !c.isNull(n)) name = c.getString(n) ?: "file"
                            if (z >= 0 && !c.isNull(z)) size = c.getLong(z)
                        }
                    }
                }
                PickedFile(name, size) { resolver.openInputStream(uri) ?: error("can't open") }
            }
            onPicked(files)
        }
    }

    ScreenFrame(
        top = { TopBar(back = "My TV", onBack = onBack, tvName = tvName) },
        bottom = { BigButton("Choose files", { picker.launch(arrayOf("*/*")) }) },
    ) {
        if (s.items.isEmpty()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMascot(mood = Mood.STAND, height = 110.dp)
                Title("Send files to your TV", align = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FileKind(Icons.Rounded.Image, "Photos", Tidy.Blue)
                    FileKind(Icons.Rounded.Movie, "Videos", Tidy.Purple)
                    FileKind(Icons.Rounded.MusicNote, "Music", Tidy.Orange)
                    FileKind(Icons.Rounded.Description, "Docs", Tidy.Green)
                }
                Body("Saved in ${s.folder.title} › TidyTV", align = TextAlign.Center)
            }
        }
        FolderChoice(s.folder, enabled = !s.busy, onFolder)
        s.freeBytes?.let { InfoChip(Icons.Rounded.SdStorage, "${formatBytes(it)} free on the TV", color = Tidy.Grey, textColor = Tidy.Ink) }
        s.items.forEach { SendRow(it, onCancel, onRetry) }
        if (s.items.isNotEmpty() && s.items.all { it.status == SendStatus.SENT }) {
            SafetyBanner("Sent. On the TV, open ${s.folder.title} › TidyTV.")
        }
    }
}

@Composable
private fun FileKind(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconBadge(icon, color, 52.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tidy.Ink)
    }
}

@Composable
private fun SendRow(item: SendItem, onCancel: (Int) -> Unit, onRetry: (Int) -> Unit) {
    Outline {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Body(formatBytes(item.size))
            when (item.status) {
                SendStatus.WAITING -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Waiting…", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted, modifier = Modifier.weight(1f))
                    TextLink("Cancel", { onCancel(item.id) })
                }
                SendStatus.SENDING -> {
                    val frac = if (item.size > 0) (item.sent.toFloat() / item.size).coerceIn(0f, 1f) else 0f
                    ProgressBar(frac)
                    Body("${formatBytes(item.sent)} of ${formatBytes(item.size)}")
                }
                SendStatus.SENT -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Check(Tidy.Green, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saved as ${item.savedAs ?: item.name}", style = MaterialTheme.typography.bodyMedium, color = Tidy.GreenInk)
                }
                SendStatus.NO_SPACE -> Note("Not enough space on the TV")
                SendStatus.FAILED -> {
                    Note("Didn't send. Check the TV is on.")
                    TextLink("Try again", { onRetry(item.id) })
                }
                SendStatus.CANCELLED -> Body("Skipped")
            }
        }
    }
}

internal fun formatBytes(b: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        b >= gb -> String.format(Locale.US, "%.1f GB", b / gb)
        b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
        else -> String.format(Locale.US, "%d KB", maxOf(1L, Math.round(b / kb)))
    }
}

/** Where on the TV the files go. Movies, Music and Pictures are where the TV's own players look first. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FolderChoice(current: dev.tidytv.core.ops.SendFolder, enabled: Boolean, onFolder: (dev.tidytv.core.ops.SendFolder) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Save in", style = MaterialTheme.typography.labelMedium, color = Tidy.Muted)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in dev.tidytv.core.ops.SendFolder.entries) {
                androidx.compose.material3.FilterChip(
                    selected = f == current, onClick = { onFolder(f) }, enabled = enabled,
                    label = { Text(f.title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
}
