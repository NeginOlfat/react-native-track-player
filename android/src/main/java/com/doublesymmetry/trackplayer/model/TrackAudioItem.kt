package com.doublesymmetry.trackplayer.model

import androidx.media3.common.util.UnstableApi
import com.doublesymmetry.kotlinaudio.models.AudioItem
import com.doublesymmetry.kotlinaudio.models.AudioItemOptions
import com.doublesymmetry.kotlinaudio.models.MediaType

@UnstableApi
data class TrackAudioItem(
    val track: Track,
    override val type: MediaType,
    override var audioUrl: String,
    override var artist: String? = null,
    override var title: String? = null,
    override var albumTitle: String? = null,
    override val artwork: String? = null,
    override val duration: Long? = null,
    override val options: AudioItemOptions? = null
): AudioItem