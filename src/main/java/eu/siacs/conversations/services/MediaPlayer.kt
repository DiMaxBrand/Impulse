package eu.siacs.conversations.services

class MediaPlayer : android.media.MediaPlayer() {
    private var streamType: Int = 0

    @Suppress("DEPRECATION")
    override fun setAudioStreamType(streamType: Int) {
        this.streamType = streamType
        super.setAudioStreamType(streamType)
    }

    fun getAudioStreamType(): Int = streamType
}
