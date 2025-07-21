package com.doublesymmetry.trackplayer.module

import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.content.ServiceConnection
import com.doublesymmetry.kotlinaudio.models.Capability
import com.doublesymmetry.kotlinaudio.models.RepeatMode
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toMilliseconds
import com.doublesymmetry.trackplayer.model.State
import com.doublesymmetry.trackplayer.model.Track
import com.doublesymmetry.trackplayer.NativeTrackPlayerSpec
import com.doublesymmetry.trackplayer.viewmodel.AudioEventViewModel
import com.doublesymmetry.trackplayer.service.MusicService
import com.doublesymmetry.trackplayer.utils.AppForegroundTracker
import com.doublesymmetry.trackplayer.utils.RejectionException
import com.facebook.react.bridge.*
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.facebook.react.module.annotations.ReactModule
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.annotation.Nonnull


/**
 * @author Milen Pivchev @mpivchev
 */
@UnstableApi
@ReactModule(name = "TrackPlayerModule")
class MusicModule(reactContext: ReactApplicationContext) :ServiceConnection,
    NativeTrackPlayerSpec(reactContext){
        
    private var playerOptions: Bundle? = null
    private var isServiceBound = false
    private var playerSetUpPromise: Promise? = null
    private val scope = MainScope()
    private lateinit var musicService: MusicService
    private val context = reactContext

    @Nonnull
    override fun getName(): String {
        return "TrackPlayerModule"
    }

    override fun invalidate() {
        // Cleanup resources when module is invalidated
        if (isServiceBound) {
            context.unbindService(this)
        }
    }

    override fun initialize(){
        Timber.plant(Timber.DebugTree())
        AppForegroundTracker.start()

    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        scope.launch {
            // If a binder already exists, don't get a new one
            if (!::musicService.isInitialized) {
                val binder: MusicService.MusicBinder = service as MusicService.MusicBinder
                musicService = binder.service
                musicService.setupPlayer(playerOptions)
                playerSetUpPromise?.resolve(null)
            }

            isServiceBound = true
        }
    }

    /**
     * Called when a connection to the Service has been lost.
     */
    override fun onServiceDisconnected(name: ComponentName) {
        scope.launch {
            isServiceBound = false
        }
    }

    /**
     * Checks wither service is bound, or rejects. Returns whether promise was rejected.
     */
    private fun verifyServiceBoundOrReject(promise: Promise): Boolean {
        if (!isServiceBound) {
            promise.reject(
                "player_not_initialized",
                "The player is not initialized. Call setupPlayer first."
            )
            return true
        }

        return false
    }

    private fun bundleToTrack(bundle: Bundle): Track {
        return Track(context, bundle, musicService.ratingType)
    }

    private fun rejectWithException(callback: Promise, exception: Exception) {
        when (exception) {
            is RejectionException -> {
                callback.reject(exception.code, exception)
            }
            else -> {
                callback.reject("runtime_exception", exception)
            }
        }
    }

    private fun readableArrayToTrackList(data: ReadableArray?): MutableList<Track> {
        val bundleList = Arguments.toList(data)
        if (bundleList !is ArrayList) {
            throw RejectionException("invalid_parameter", "Was not given an array of tracks")
        }
        return bundleList.map {
            if (it is Bundle) {
                bundleToTrack(it)
            } else {
                throw RejectionException(
                    "invalid_track_object",
                    "Track was not a dictionary type"
                )
            }
        }.toMutableList()
    }

    /* ****************************** API ****************************** */
       override fun getTypedExportedConstants(): Map<String, Any> {
        return hashMapOf<String, Any>().apply {
           // Capabilities
            put("CAPABILITY_PLAY", Capability.PLAY.ordinal)
            put("CAPABILITY_PLAY_FROM_ID", Capability.PLAY_FROM_ID.ordinal)
            put("CAPABILITY_PLAY_FROM_SEARCH", Capability.PLAY_FROM_SEARCH.ordinal)
            put("CAPABILITY_PAUSE", Capability.PAUSE.ordinal)
            put("CAPABILITY_STOP", Capability.STOP.ordinal)
            put("CAPABILITY_SEEK_TO", Capability.SEEK_TO.ordinal)
            put("CAPABILITY_SKIP", OnErrorAction.SKIP.ordinal)
            put("CAPABILITY_SKIP_TO_NEXT", Capability.SKIP_TO_NEXT.ordinal)
            put("CAPABILITY_SKIP_TO_PREVIOUS", Capability.SKIP_TO_PREVIOUS.ordinal)
            put("CAPABILITY_SET_RATING", Capability.SET_RATING.ordinal)
            put("CAPABILITY_JUMP_FORWARD", Capability.JUMP_FORWARD.ordinal)
            put("CAPABILITY_JUMP_BACKWARD", Capability.JUMP_BACKWARD.ordinal)

            // States
            put("STATE_NONE", State.None.state)
            put("STATE_READY", State.Ready.state)
            put("STATE_PLAYING", State.Playing.state)
            put("STATE_PAUSED", State.Paused.state)
            put("STATE_STOPPED", State.Stopped.state)
            put("STATE_BUFFERING", State.Buffering.state)
            put("STATE_LOADING", State.Loading.state)

            // Rating Types
            put("RATING_HEART", 1)
            put("RATING_THUMBS_UP_DOWN", 2)
            put("RATING_3_STARS", 3)
            put("RATING_4_STARS", 4)
            put("RATING_5_STARS", 5)
            put("RATING_PERCENTAGE", 6)

            // Repeat Modes
            put("REPEAT_OFF", Player.REPEAT_MODE_OFF)
            put("REPEAT_TRACK", Player.REPEAT_MODE_ONE)
            put("REPEAT_QUEUE", Player.REPEAT_MODE_ALL)
        }
    }


    override fun setupPlayer(data: ReadableMap?, promise: Promise) {
        if (isServiceBound) {
            promise.reject(
                "player_already_initialized",
                "The player has already been initialized via setupPlayer."
            )
            return
        }

        // prevent crash Fatal Exception: android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppForegroundTracker.backgrounded) {
            promise.reject(
                "android_cannot_setup_player_in_background",
                "On Android the app must be in the foreground when setting up the player."
            )
            return
        }

        // Validate buffer keys.
        val DEFAULT_MIN_BUFFER_MS = 2500 // 2.5 seconds
        val DEFAULT_MAX_BUFFER_MS = 5000 // 5 seconds
        val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 3000 // 3 seconds
        val DEFAULT_BACK_BUFFER_DURATION_MS = 1000 // 1 second

        fun Double.toMilliseconds(): Int = (this * 1000).toInt()

        val bundledData = Arguments.toBundle(data)
        val minBuffer =
            bundledData?.getDouble(MusicService.MIN_BUFFER_KEY)?.toMilliseconds()
                ?: DEFAULT_MIN_BUFFER_MS
        val maxBuffer =
            bundledData?.getDouble(MusicService.MAX_BUFFER_KEY)?.toMilliseconds()
                ?: DEFAULT_MAX_BUFFER_MS
        val playBuffer =
            bundledData?.getDouble(MusicService.PLAY_BUFFER_KEY)?.toMilliseconds()
                ?: DEFAULT_BUFFER_FOR_PLAYBACK_MS
        val backBuffer =
            bundledData?.getDouble(MusicService.BACK_BUFFER_KEY)?.toMilliseconds()
                ?: DEFAULT_BACK_BUFFER_DURATION_MS

        if (playBuffer < 0) {
            promise.reject(
                "play_buffer_error",
                "The value for playBuffer should be greater than or equal to zero."
            )
            return
        }

        if (backBuffer < 0) {
            promise.reject(
                "back_buffer_error",
                "The value for backBuffer should be greater than or equal to zero."
            )
            return
        }

        if (minBuffer < playBuffer) {
            promise.reject(
                "min_buffer_error",
                "The value for minBuffer should be greater than or equal to playBuffer."
            )
            return
        }

        if (maxBuffer < minBuffer) {
            promise.reject(
                "min_buffer_error",
                "The value for maxBuffer should be greater than or equal to minBuffer."
            )
            return
        }

        playerSetUpPromise = promise
        playerOptions = bundledData

        val intent = Intent(context, MusicService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
    }


    @Deprecated("Backwards compatible function from the old android implementation. Should be removed in the next major release.")
    override fun isServiceRunning(callback: Promise) {
        callback.resolve(isServiceBound)
    }

    override fun updateOptions(data: ReadableMap?, callback: Promise)  {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            val options = Arguments.toBundle(data)

            options?.let {
                musicService.updateOptions(it)
            }

            callback.resolve(null)
        }
    }

    override fun add(data: ReadableArray?, insertBeforeIndex: Double, callback: Promise) {
        scope.launch {
            val insertBeforeIndexInt = insertBeforeIndex.toInt()
            if (verifyServiceBoundOrReject(callback)) return@launch

            try {
                val tracks = readableArrayToTrackList(data);
                if (insertBeforeIndexInt < -1 || insertBeforeIndexInt > musicService.tracks.size) {
                    callback.reject("index_out_of_bounds", "The track index is out of bounds")
                    return@launch
                }
                val index =
                    if (insertBeforeIndexInt == -1) musicService.tracks.size else insertBeforeIndexInt
                musicService.add(
                    tracks,
                    index
                )
                callback.resolve(index)
            } catch (exception: Exception) {
                rejectWithException(callback, exception)
            }
        }
    }

    override fun load(data: ReadableMap?, callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch
            if (data == null) {
                callback.resolve(null)
                return@launch
            }
            val bundle = Arguments.toBundle(data);
            if (bundle is Bundle) {
                musicService.load(bundleToTrack(bundle))
                callback.resolve(null)
            } else {
                callback.reject("invalid_track_object", "Track was not a dictionary type")
            }
        }
    }

    override fun move(fromIndex: Double, toIndex: Double, callback: Promise)  {
        scope.launch {
            val fromIndexInt= fromIndex.toInt()
            val toIndexInt = toIndex.toInt()
            if (verifyServiceBoundOrReject(callback)) return@launch
            musicService.move(fromIndexInt, toIndexInt)
            callback.resolve(null)
        }
    }

    override fun remove(data: ReadableArray?, callback: Promise)  {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch
            val inputIndexes = Arguments.toList(data)
            if (inputIndexes != null) {
                val size = musicService.tracks.size
                val indexes: ArrayList<Int> = ArrayList();
                for (inputIndex in inputIndexes) {
                    val index = if (inputIndex is Int) inputIndex else inputIndex.toString().toInt()
                    if (index < 0 || index >= size) {
                        callback.reject(
                            "index_out_of_bounds",
                            "One or more indexes was out of bounds"
                        )
                        return@launch
                    }
                    indexes.add(index)
                }
                musicService.remove(indexes)
            }
            callback.resolve(null)
        }
    }

    override fun updateMetadataForTrack(index: Double, map: ReadableMap?, callback: Promise) {
        scope.launch {
            val indexInt = index.toInt()
            if (verifyServiceBoundOrReject(callback)) return@launch

            if (indexInt < 0 || indexInt >= musicService.tracks.size) {
                callback.reject("index_out_of_bounds", "The index is out of bounds")
            } else {
                val context: ReactContext = this@MusicModule.context
                val track = musicService.tracks[indexInt]
                track.setMetadata(context, Arguments.toBundle(map), musicService.ratingType)
                musicService.updateMetadataForTrack(indexInt, track)

                callback.resolve(null)
            }
        }
    }

    override fun updateNowPlayingMetadata(map: ReadableMap?, callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            if (musicService.tracks.isEmpty())
                callback.reject("no_current_item", "There is no current item in the player")

            val context: ReactContext = this@MusicModule.context
            Arguments.toBundle(map)?.let {
                val track = bundleToTrack(it)
                musicService.updateNowPlayingMetadata(track)
            }

            callback.resolve(null)
        }
    }

    override fun clearNowPlayingMetadata(callback: Promise)  {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            if (musicService.tracks.isEmpty())
                callback.reject("no_current_item", "There is no current item in the player")

            musicService.clearNotificationMetadata()
            callback.resolve(null)
        }
    }

    override fun removeUpcomingTracks(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.removeUpcomingTracks()
            callback.resolve(null)
        }
    }

    override fun skip(index: Double, initialTime: Double, callback: Promise) {
        scope.launch {
            val indexInt =index.toInt()
            val initialTimeFloat = initialTime.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.skip(indexInt)

            if (initialTimeFloat >= 0) {
                musicService.seekTo(initialTimeFloat)
            }

            callback.resolve(null)
        }
    }

    override fun skipToNext(initialTime: Double, callback: Promise) {
        scope.launch {
            val initialTimeFloat = initialTime.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.skipToNext()

            if (initialTimeFloat >= 0) {
                musicService.seekTo(initialTimeFloat)
            }

            callback.resolve(null)
        }
    }

    override fun skipToPrevious(initialTime: Double, callback: Promise)  {
        scope.launch {
            val initialTimeFloat = initialTime.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.skipToPrevious()

            if (initialTimeFloat >= 0) {
                musicService.seekTo(initialTimeFloat)
            }

            callback.resolve(null)
        }
    }

    override fun reset(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.stop()
            delay(300) // Allow playback to stop
            musicService.clear()

            callback.resolve(null)
        }
    }

    override fun play(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.play()
            callback.resolve(null)
        }
    }

    override fun pause(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.pause()
            callback.resolve(null)
        }
    }

    override fun stop(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.stop()
            callback.resolve(null)
        }
    }

    override fun seekTo(seconds: Double, callback: Promise) {
        scope.launch {
            val secondsFloat = seconds.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.seekTo(secondsFloat)
            callback.resolve(null)
        }
    }

    override fun seekBy(offset: Double, callback: Promise) {
        scope.launch {
            val offsetFloat = offset.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.seekBy(offsetFloat)
            callback.resolve(null)
        }
    }

    override fun retry(callback: Promise)  {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.retry()
            callback.resolve(null)
        }
    }

    override fun setVolume(volume: Double, callback: Promise) {
        scope.launch {
            val volumeFloat = volume.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.setVolume(volumeFloat)
            callback.resolve(null)
        }
    }

    override fun getVolume(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getVolume())
        }
    }

    override fun setRate(rate: Double, callback: Promise) {
        scope.launch {
            val rateFloat = rate.toFloat()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.setRate(rateFloat)
            callback.resolve(null)
        }
    }

    override fun getRate(callback: Promise)  {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getRate())
        }
    }

    override fun setRepeatMode(mode: Double, callback: Promise) {
        scope.launch {
            val modeInt = mode.toInt()
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.setRepeatMode(RepeatMode.fromOrdinal(modeInt))
            callback.resolve(null)
        }
    }

    override fun getRepeatMode(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getRepeatMode().ordinal)
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean, callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            musicService.playWhenReady = playWhenReady
            callback.resolve(null)
        }
    }

    override fun getPlayWhenReady(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.playWhenReady)
        }
    }

    override fun getTrack(index: Double, callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            val indexInt = index.toInt()
            val track = musicService.tracks.getOrNull(indexInt)
            val originalItem = track?.originalItem

            if (originalItem is Bundle) {
                callback.resolve(Arguments.fromBundle(originalItem))
            } else {
                callback.resolve(null)
            }
        }
    }


    override fun getQueue(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(Arguments.fromList(musicService.tracks.map { it.originalItem }))
        }
    }

    override fun setQueue(data: ReadableArray?, callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            try {
                musicService.clear()
                musicService.add(readableArrayToTrackList(data))
                callback.resolve(null)
            } catch (exception: Exception) {
                rejectWithException(callback, exception)
            }
        }
    }

    override fun getActiveTrackIndex(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch
            callback.resolve(
                if (musicService.tracks.isEmpty()) null else musicService.getCurrentTrackIndex()
            )
        }
    }

    override fun getActiveTrack(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            val index = musicService.getCurrentTrackIndex()
            val track = musicService.tracks.getOrNull(index)
            val originalItem = track?.originalItem

            if (originalItem is Bundle) {
                callback.resolve(Arguments.fromBundle(originalItem))
            } else {
                callback.resolve(null)
            }
        }
    }


    override fun getDuration(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getDurationInSeconds())
        }
    }

    override fun getBufferedPosition(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getBufferedPositionInSeconds())
        }
    }

    override fun getPosition(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch

            callback.resolve(musicService.getPositionInSeconds())
        }
    }

    override fun getProgress(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch
            var bundle = Bundle()
            bundle.putDouble("duration", musicService.getDurationInSeconds());
            bundle.putDouble("position", musicService.getPositionInSeconds());
            bundle.putDouble("buffered", musicService.getBufferedPositionInSeconds());
            callback.resolve(Arguments.fromBundle(bundle))
        }
    }

    override fun getPlaybackState(callback: Promise) {
        scope.launch {
            if (verifyServiceBoundOrReject(callback)) return@launch
            callback.resolve(Arguments.fromBundle(musicService.getPlayerStateBundle(musicService.state)))
        }
    }
}
