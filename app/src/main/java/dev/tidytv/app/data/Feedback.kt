package dev.tidytv.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** What kind of message the user is sending. [wire] is the value the Winkle endpoint expects. */
enum class FeedbackKind(val wire: String, val label: String) {
    BUG("bug", "Something went wrong"),
    PRAISE("praise", "It worked great"),
    FEATURE("feature", "An idea"),
    QUESTION("question", "A question"),
}

/** One message from the feedback sheet, before device details are added. */
data class FeedbackDraft(
    val kind: FeedbackKind,
    val message: String,
    val email: String = "",
    val page: String = "",
    /** The shared log text (already scrubbed of serials, Wi-Fi names and addresses), or null. */
    val log: String? = null,
    /** A JPEG, already resized. */
    val screenshot: ByteArray? = null,
)
