package dev.tidytv.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Who made 0TV, how to reach them, and the open-source work it stands on. */
@Composable
fun AboutSheet(onDismiss: () -> Unit, openUrl: (String) -> Unit) {
    InfoSheet("About 0TV", onDismiss) { AboutContent(openUrl) }
}

@Composable
fun AboutContent(openUrl: (String) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text("0TV", style = MaterialTheme.typography.headlineMedium, color = Tidy.Ink)
        Text("Clean up your Android TV, then give it a calmer Home screen. Review changes before applying them, with backups and restore tools.", style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted)
        Text("Author", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.padding(top = 6.dp))
        Text("ARNAB MANDAL", style = MaterialTheme.typography.titleLarge, color = Tidy.Ink)
        Column {
            TextLink("arnabmandal.com", { openUrl("https://arnabmandal.com") })
            TextLink("arnab@ik.me", { openUrl("mailto:arnab@ik.me") })
            TextLink("github.com/vio137", { openUrl("https://github.com/vio137") })
        }
        Text("Built on open-source work", style = MaterialTheme.typography.titleMedium, color = Tidy.Ink, modifier = Modifier.padding(top = 6.dp))
        Text(
            "0TV started from the MIT-licensed TidyTV, cleanyourtv (Gathin) and tv-detox (Ansub) projects and keeps their copyright notices. " +
                "It also uses spake2-java (GPL-3.0, so this app is shared under GPL-3.0), Kadb (Apache-2.0) and other libraries. " +
                "0TV as a product, its design and its changes to that code are by ARNAB MANDAL (the upstream projects are by their own authors). " +
                "Full licence texts are in the source package.",
            style = MaterialTheme.typography.bodyMedium, color = Tidy.Muted,
        )
    }
}
