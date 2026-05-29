package eu.siacs.conversations.utils

import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy

object TranscoderStrategies {

    @JvmField
    val VIDEO_1080P: DefaultVideoStrategy = DefaultVideoStrategy.atMost(1080)
        .bitRate(4L * 1000 * 1000)
        .frameRate(30)
        .keyFrameInterval(3F)
        .build()

    @JvmField
    val VIDEO_720P: DefaultVideoStrategy = DefaultVideoStrategy.atMost(720)
        .bitRate(2L * 1000 * 1000)
        .frameRate(30)
        .keyFrameInterval(3F)
        .build()

    @JvmField
    val VIDEO_480P: DefaultVideoStrategy = DefaultVideoStrategy.atMost(480)
        .bitRate(1000 * 1000)
        .frameRate(30)
        .keyFrameInterval(3F)
        .build()

    @JvmField
    val VIDEO_360P: DefaultVideoStrategy = DefaultVideoStrategy.atMost(360)
        .bitRate(500 * 1000)
        .frameRate(30)
        .keyFrameInterval(3F)
        .build()

    // TODO do we want to add 240p (@500kbs) and 1080p (@4mbs?) ?
    // see suggested bit rates on
    // https://www.videoproc.com/media-converter/bitrate-setting-for-h264.htm

    @JvmField
    val AUDIO_HQ: DefaultAudioStrategy = DefaultAudioStrategy.builder()
        .bitRate(192 * 1000)
        .channels(2)
        .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
        .build()

    @JvmField
    val AUDIO_MQ: DefaultAudioStrategy = DefaultAudioStrategy.builder()
        .bitRate(96 * 1000)
        .channels(2)
        .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
        .build()

    // TODO if we add 144p we definitely want to add a lower audio bit rate as well
}
