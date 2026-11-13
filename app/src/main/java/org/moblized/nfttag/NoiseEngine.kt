package org.moblized.nfttag

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

/** The three noise "colors" the app can generate on the fly (no audio files). */
enum class NoiseColor { WHITE, PINK, BROWN }

/**
 * Generates continuous noise directly as PCM and streams it to an [AudioTrack]
 * on a background thread. No sample files, so it loops forever with no seam.
 */
class NoiseEngine {

    private val sampleRate = 44_100
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var color: NoiseColor = NoiseColor.WHITE
    @Volatile private var gain: Float = 0.6f

    val isPlaying: Boolean get() = running

    fun start(color: NoiseColor, gain: Float) {
        if (running) {
            setColor(color)
            setGain(gain)
            return
        }
        this.color = color
        this.gain = gain

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, sampleRate) // ~1s of headroom

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = at
        at.play()
        running = true

        thread = Thread {
            val frames = ShortArray(2048)
            // Pink/brown filter state
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
            var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
            var brown = 0.0

            while (running) {
                val g = gain
                for (i in frames.indices) {
                    val white = Random.nextDouble(-1.0, 1.0)
                    val sample = when (color) {
                        NoiseColor.WHITE -> white
                        NoiseColor.PINK -> {
                            // Paul Kellet's refined pink-noise filter
                            b0 = 0.99886 * b0 + white * 0.0555179
                            b1 = 0.99332 * b1 + white * 0.0750759
                            b2 = 0.96900 * b2 + white * 0.1538520
                            b3 = 0.86650 * b3 + white * 0.3104856
                            b4 = 0.55000 * b4 + white * 0.5329522
                            b5 = -0.7616 * b5 - white * 0.0168980
                            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                            b6 = white * 0.115926
                            pink * 0.11
                        }
                        NoiseColor.BROWN -> {
                            brown = (brown + 0.02 * white).coerceIn(-1.0, 1.0)
                            brown * 3.5
                        }
                    }
                    val v = (sample * g * Short.MAX_VALUE)
                        .coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                    frames[i] = v.toInt().toShort()
                }
                val t = track ?: break
                t.write(frames, 0, frames.size)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun setColor(c: NoiseColor) { color = c }

    fun setGain(g: Float) {
        gain = g.coerceIn(0f, 1f)
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.let {
            try {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        track = null
    }
}
