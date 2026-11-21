package com.example.data.prefs

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Context.ringtoneDataStore by preferencesDataStore(name = "ringtone_prefs")

enum class IncomingToneMode(val label: String, val subtitle: String) {
    IOS_REFLECTION("Reflection (iOS)", "Warm bell marimba arpeggio"),
    IOS_MARIMBA("Marimba Classic", "Iconic crisp percussive melody"),
    IOS_OPENING("Opening Chime", "Bright glass chime motif"),
    VIRAL_AURORA("Aurora Viral Pulse", "Modern rhythmic synth drop"),
    VIRAL_SILK("Silk Horizon", "Soft ambient crystal tones"),
    SYSTEM_DEFAULT("Phone Default", "Uses device default ringtone"),
    CUSTOM_URI("Custom Audio", "System picker or local MP3/audio");

    companion object {
        fun fromRaw(raw: String?): IncomingToneMode = when (raw) {
            "PRESET_PULSE" -> VIRAL_AURORA
            "PRESET_CHIME" -> IOS_OPENING
            else -> entries.firstOrNull { it.name == raw } ?: IOS_REFLECTION
        }
    }
}

enum class DialToneMode(val label: String) {
    IOS_DIALING("iPhone Soft Ringback"),
    PRESET_CLASSIC("Classic Ringback (Tu... Tu...)"),
    PRESET_SOFT("Aurora Soft Dial"),
    CUSTOM_URI("Custom Dial / Caller Tune"),
    SILENT("Silent Outgoing Dial");

    companion object {
        fun fromRaw(raw: String?): DialToneMode =
            entries.firstOrNull { it.name == raw } ?: IOS_DIALING
    }
}

enum class ChatBubbleStyle(val label: String, val subtitle: String) {
    IOS_GLASS("iOS Frosted Azure", "Signature liquid glass Azure & frosted white"),
    CLASSIC_ROUNDED("Classic Rounded", "Clean solid modern rounded chat bubbles"),
    EMERALD_MINT("Emerald Mint", "Fresh teal-emerald accent for outgoing chats"),
    MIDNIGHT_NEON("Midnight Sapphire", "Deep cobalt glass glow for night reading"),
    MINIMAL_OUTLINE("Minimal Hairline", "Crisp editorial outline bubbles");

    companion object {
        fun fromRaw(raw: String?): ChatBubbleStyle =
            entries.firstOrNull { it.name == raw } ?: IOS_GLASS
    }
}

data class RingtoneConfig(
    val incomingMode: IncomingToneMode = IncomingToneMode.IOS_REFLECTION,
    val incomingUri: String = "",
    val incomingLabel: String = IncomingToneMode.IOS_REFLECTION.label,
    val dialMode: DialToneMode = DialToneMode.IOS_DIALING,
    val dialUri: String = "",
    val dialLabel: String = DialToneMode.IOS_DIALING.label,
    val darkThemeEnabled: Boolean = false,
    val touchSoundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val customImgBbKey: String = "",
    val cloudinaryCloudName: String = "",
    val cloudinaryUploadPreset: String = "",
    val safTreeUri: String = "",
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.IOS_GLASS,
    val defaultChatBgPreset: String = "preset_default",
    val incomingCallBackgroundUri: String = "",
    val biometricLockEnabled: Boolean = true,
    val biometricAllowPinFallback: Boolean = true
)

class RingtonePreferences(private val context: Context) {

    private object Keys {
        val INCOMING_MODE = stringPreferencesKey("incoming_mode")
        val INCOMING_URI = stringPreferencesKey("incoming_uri")
        val INCOMING_LABEL = stringPreferencesKey("incoming_label")
        val DIAL_MODE = stringPreferencesKey("dial_mode")
        val DIAL_URI = stringPreferencesKey("dial_uri")
        val DIAL_LABEL = stringPreferencesKey("dial_label")
        val DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        val TOUCH_SOUND = booleanPreferencesKey("touch_sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val CUSTOM_IMGBB_KEY = stringPreferencesKey("custom_imgbb_key")
        val CLOUDINARY_CLOUD = stringPreferencesKey("cloudinary_cloud_name")
        val CLOUDINARY_PRESET = stringPreferencesKey("cloudinary_upload_preset")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val CHAT_BUBBLE_STYLE = stringPreferencesKey("chat_bubble_style")
        val DEFAULT_CHAT_BG = stringPreferencesKey("default_chat_bg")
        val INCOMING_CALL_BG_URI = stringPreferencesKey("incoming_call_bg_uri")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val BIOMETRIC_PIN_FALLBACK = booleanPreferencesKey("biometric_pin_fallback")
    }

    val configFlow: Flow<RingtoneConfig> = context.applicationContext.ringtoneDataStore.data.map { prefs ->
        val incomingMode = IncomingToneMode.fromRaw(prefs[Keys.INCOMING_MODE])
        val dialMode = DialToneMode.fromRaw(prefs[Keys.DIAL_MODE])
        RingtoneConfig(
            incomingMode = incomingMode,
            incomingUri = prefs[Keys.INCOMING_URI].orEmpty(),
            incomingLabel = prefs[Keys.INCOMING_LABEL].orEmpty().ifBlank { incomingMode.label },
            dialMode = dialMode,
            dialUri = prefs[Keys.DIAL_URI].orEmpty(),
            dialLabel = prefs[Keys.DIAL_LABEL].orEmpty().ifBlank { dialMode.label },
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: false,
            touchSoundEnabled = prefs[Keys.TOUCH_SOUND] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            customImgBbKey = prefs[Keys.CUSTOM_IMGBB_KEY].orEmpty(),
            cloudinaryCloudName = prefs[Keys.CLOUDINARY_CLOUD].orEmpty(),
            cloudinaryUploadPreset = prefs[Keys.CLOUDINARY_PRESET].orEmpty(),
            safTreeUri = prefs[Keys.SAF_TREE_URI].orEmpty(),
            chatBubbleStyle = ChatBubbleStyle.fromRaw(prefs[Keys.CHAT_BUBBLE_STYLE]),
            defaultChatBgPreset = prefs[Keys.DEFAULT_CHAT_BG].orEmpty().ifBlank { "preset_default" },
            incomingCallBackgroundUri = prefs[Keys.INCOMING_CALL_BG_URI].orEmpty(),
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: true,
            biometricAllowPinFallback = prefs[Keys.BIOMETRIC_PIN_FALLBACK] ?: true
        )
    }

    suspend fun getConfig(): RingtoneConfig = configFlow.first()

    suspend fun setIncomingTone(
        mode: IncomingToneMode,
        uri: String = "",
        label: String = mode.label
    ) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.INCOMING_MODE] = mode.name
            prefs[Keys.INCOMING_URI] = uri
            prefs[Keys.INCOMING_LABEL] = label
        }
    }

    suspend fun setDialTone(
        mode: DialToneMode,
        uri: String = "",
        label: String = mode.label
    ) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.DIAL_MODE] = mode.name
            prefs[Keys.DIAL_URI] = uri
            prefs[Keys.DIAL_LABEL] = label
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun setTouchSoundEnabled(enabled: Boolean) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.TOUCH_SOUND] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.HAPTICS] = enabled
        }
    }

    suspend fun setCustomImgBbKey(key: String) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_IMGBB_KEY] = key.trim()
        }
    }

    suspend fun setCloudinaryConfig(cloudName: String, uploadPreset: String) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.CLOUDINARY_CLOUD] = cloudName.trim()
            prefs[Keys.CLOUDINARY_PRESET] = uploadPreset.trim()
        }
    }

    suspend fun setSafTreeUri(uriString: String) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.SAF_TREE_URI] = uriString.trim()
        }
    }

    suspend fun setChatBubbleStyle(style: ChatBubbleStyle) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.CHAT_BUBBLE_STYLE] = style.name
        }
    }

    suspend fun setDefaultChatBgPreset(presetOrUrl: String) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_CHAT_BG] = presetOrUrl.trim().ifBlank { "preset_default" }
        }
    }

    suspend fun setIncomingCallBackgroundUri(uriOrUrl: String) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.INCOMING_CALL_BG_URI] = uriOrUrl.trim()
        }
    }

    suspend fun setBiometricSettings(enabled: Boolean, allowPinFallback: Boolean) {
        context.applicationContext.ringtoneDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK] = enabled
            prefs[Keys.BIOMETRIC_PIN_FALLBACK] = allowPinFallback
        }
    }
}

class RingtoneAudioController(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var pcmTrack: AudioTrack? = null
    private var pcmLoopJob: Job? = null
    private var autoStopJob: Job? = null

    fun playIncomingRingtone(config: RingtoneConfig, previewOnly: Boolean = false) {
        stop()
        when (config.incomingMode) {
            IncomingToneMode.IOS_REFLECTION -> {
                playSynthesizedLoop(
                    isCallStream = false,
                    notes = listOf(
                        SynthNote(783.99, 135, 25),   // G5
                        SynthNote(659.25, 135, 25),   // E5
                        SynthNote(987.77, 135, 25),   // B5
                        SynthNote(1174.66, 260, 45),  // D6
                        SynthNote(987.77, 150, 25),   // B5
                        SynthNote(783.99, 320, 70),   // G5
                        SynthNote(880.00, 150, 25),   // A5
                        SynthNote(987.77, 420, 750)   // B5
                    ),
                    bellHarmonics = true
                )
            }
            IncomingToneMode.IOS_MARIMBA -> {
                playSynthesizedLoop(
                    isCallStream = false,
                    notes = listOf(
                        SynthNote(523.25, 120, 30),   // C5
                        SynthNote(659.25, 120, 30),   // E5
                        SynthNote(783.99, 120, 30),   // G5
                        SynthNote(1046.50, 180, 40),  // C6
                        SynthNote(987.77, 120, 30),   // B5
                        SynthNote(783.99, 140, 35),   // G5
                        SynthNote(659.25, 140, 35),   // E5
                        SynthNote(587.33, 280, 700)   // D5
                    ),
                    bellHarmonics = true
                )
            }
            IncomingToneMode.IOS_OPENING -> {
                playSynthesizedLoop(
                    isCallStream = false,
                    notes = listOf(
                        SynthNote(587.33, 150, 30),   // D5
                        SynthNote(880.00, 150, 30),   // A5
                        SynthNote(1174.66, 240, 40),  // D6
                        SynthNote(1318.51, 340, 120), // E6
                        SynthNote(1174.66, 180, 30),  // D6
                        SynthNote(880.00, 320, 680)   // A5
                    ),
                    bellHarmonics = true
                )
            }
            IncomingToneMode.VIRAL_AURORA -> {
                playSynthesizedLoop(
                    isCallStream = false,
                    notes = listOf(
                        SynthNote(659.25, 110, 35),   // E5
                        SynthNote(783.99, 110, 35),   // G5
                        SynthNote(987.77, 110, 35),   // B5
                        SynthNote(1318.51, 220, 50),  // E6
                        SynthNote(1174.66, 120, 35),  // D6
                        SynthNote(987.77, 120, 35),   // B5
                        SynthNote(783.99, 260, 60),   // G5
                        SynthNote(1046.50, 280, 650)  // C6
                    ),
                    bellHarmonics = true
                )
            }
            IncomingToneMode.VIRAL_SILK -> {
                playSynthesizedLoop(
                    isCallStream = false,
                    notes = listOf(
                        SynthNote(523.25, 220, 40),   // C5
                        SynthNote(783.99, 220, 40),   // G5
                        SynthNote(987.77, 300, 60),   // B5
                        SynthNote(880.00, 260, 40),   // A5
                        SynthNote(659.25, 360, 750)   // E5
                    ),
                    bellHarmonics = true
                )
            }
            IncomingToneMode.SYSTEM_DEFAULT -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                playRingtoneUri(uri)
            }
            IncomingToneMode.CUSTOM_URI -> {
                val parsed = config.incomingUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                if (parsed != null) {
                    if (!playCustomAudioUri(parsed, isCallStream = false)) {
                        playRingtoneUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                    }
                } else {
                    playRingtoneUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                }
            }
        }

        if (previewOnly) {
            scheduleAutoStop(3800L)
        }
    }

    fun playOutgoingDialTone(config: RingtoneConfig, previewOnly: Boolean = false) {
        stop()
        when (config.dialMode) {
            DialToneMode.SILENT -> return
            DialToneMode.IOS_DIALING -> {
                playSynthesizedLoop(
                    isCallStream = true,
                    notes = listOf(
                        SynthNote(440.0, 240, 60),
                        SynthNote(554.37, 360, 1450)
                    ),
                    bellHarmonics = true,
                    gain = 0.28f
                )
            }
            DialToneMode.PRESET_CLASSIC -> {
                playSynthesizedLoop(
                    isCallStream = true,
                    notes = listOf(
                        SynthNote(440.0, 950, 1850)
                    ),
                    bellHarmonics = false,
                    secondFreqHz = 480.0,
                    gain = 0.25f
                )
            }
            DialToneMode.PRESET_SOFT -> {
                playSynthesizedLoop(
                    isCallStream = true,
                    notes = listOf(
                        SynthNote(523.25, 200, 120),
                        SynthNote(659.25, 240, 1500)
                    ),
                    bellHarmonics = true,
                    gain = 0.26f
                )
            }
            DialToneMode.CUSTOM_URI -> {
                val parsed = config.dialUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                if (parsed == null || !playCustomAudioUri(parsed, isCallStream = true)) {
                    playSynthesizedLoop(
                        isCallStream = true,
                        notes = listOf(SynthNote(440.0, 240, 60), SynthNote(554.37, 360, 1450)),
                        bellHarmonics = true,
                        gain = 0.28f
                    )
                }
            }
        }

        if (previewOnly) {
            scheduleAutoStop(3800L)
        }
    }

    private fun scheduleAutoStop(delayMs: Long) {
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(delayMs)
            stop()
        }
    }

    private fun playRingtoneUri(uri: Uri?) {
        if (uri == null) return
        try {
            ringtone = RingtoneManager.getRingtone(appContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play ringtone URI", e)
        }
    }

    private fun playCustomAudioUri(uri: Uri, isCallStream: Boolean): Boolean {
        return try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (isCallStream) {
                                AudioAttributes.USAGE_VOICE_COMMUNICATION
                            } else {
                                AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                            }
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, uri)
                isLooping = true
                setVolume(0.78f, 0.78f)
                prepare()
                start()
            }
            mediaPlayer = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play custom URI $uri", e)
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
            false
        }
    }

    private data class SynthNote(
        val freqHz: Double,
        val durationMs: Int,
        val gapMs: Int
    )

    private fun playSynthesizedLoop(
        isCallStream: Boolean,
        notes: List<SynthNote>,
        bellHarmonics: Boolean,
        secondFreqHz: Double? = null,
        gain: Float = 0.42f
    ) {
        pcmLoopJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val pcm = buildLoopPcm(sampleRate, notes, bellHarmonics, secondFreqHz, gain)
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val attrs = AudioAttributes.Builder()
                .setUsage(
                    if (isCallStream) {
                        AudioAttributes.USAGE_VOICE_COMMUNICATION
                    } else {
                        AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                    }
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = runCatching {
                AudioTrack(
                    attrs,
                    format,
                    maxOf(minBuf, pcm.size * 2),
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }.getOrNull() ?: return@launch

            pcmTrack = track
            try {
                track.write(pcm, 0, pcm.size)
                track.setLoopPoints(0, pcm.size, -1)
                track.play()
                while (isActive) {
                    delay(200L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "PCM ringtone playback stopped", e)
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
                if (pcmTrack === track) {
                    pcmTrack = null
                }
            }
        }
    }

    private fun buildLoopPcm(
        sampleRate: Int,
        notes: List<SynthNote>,
        bellHarmonics: Boolean,
        secondFreqHz: Double?,
        gain: Float
    ): ShortArray {
        val totalSamples = notes.sumOf { ((it.durationMs + it.gapMs) * sampleRate) / 1000 }
        val buffer = ShortArray(totalSamples.coerceAtLeast(1))
        var cursor = 0
        for (note in notes) {
            val toneSamples = (note.durationMs * sampleRate) / 1000
            val gapSamples = (note.gapMs * sampleRate) / 1000
            val attack = (0.012 * sampleRate).toInt().coerceAtLeast(1)
            val release = (0.045 * sampleRate).toInt().coerceAtLeast(1)

            for (i in 0 until toneSamples) {
                if (cursor >= buffer.size) break
                val t = i.toDouble() / sampleRate.toDouble()
                val env = when {
                    i < attack -> i.toDouble() / attack.toDouble()
                    i > toneSamples - release -> {
                        val rem = (toneSamples - i).coerceAtLeast(0)
                        0.5 * (1.0 - cos(PI * rem / release.toDouble()))
                    }
                    bellHarmonics -> kotlin.math.exp(-2.8 * t)
                    else -> 1.0
                }
                val wave = if (secondFreqHz != null) {
                    0.55 * sin(2.0 * PI * note.freqHz * t) + 0.45 * sin(2.0 * PI * secondFreqHz * t)
                } else if (bellHarmonics) {
                    0.68 * sin(2.0 * PI * note.freqHz * t) +
                        0.24 * sin(2.0 * PI * (note.freqHz * 2.0) * t) +
                        0.08 * sin(2.0 * PI * (note.freqHz * 3.0) * t)
                } else {
                    sin(2.0 * PI * note.freqHz * t)
                }
                val sample = (wave * env * gain * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[cursor++] = sample.toShort()
            }
            cursor = min(buffer.size, cursor + gapSamples)
        }
        return buffer
    }

    fun stop() {
        autoStopJob?.cancel()
        autoStopJob = null
        pcmLoopJob?.cancel()
        pcmLoopJob = null
        try {
            pcmTrack?.pause()
            pcmTrack?.flush()
            pcmTrack?.release()
        } catch (_: Exception) {}
        pcmTrack = null
        try {
            ringtone?.stop()
        } catch (_: Exception) {}
        ringtone = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private companion object {
        const val TAG = "RingtoneAudioCtrl"
    }
}
