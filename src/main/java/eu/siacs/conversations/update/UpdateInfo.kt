package eu.siacs.conversations.update

data class UpdateInfo(
    val versionName: String,
    val channel: UpdateChannel,
    val downloadUrl: String,
    val releaseNotes: String,
    // GitHub release title (e.g. "1.12.0: Better voice messages") — blank for releases created
    // without a custom title (GitHub then defaults it to the tag name, which isn't worth
    // preferring over the app name), in which case callers should fall back to just "Impulse".
    val releaseTitle: String = "",
)
