package eu.siacs.conversations.update

data class UpdateInfo(
    val versionName: String,
    val channel: UpdateChannel,
    val downloadUrl: String,
    val releaseNotes: String,
)
