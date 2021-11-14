@file:Suppress("ArrayInDataClass")

package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.*
import dev.kord.voice.encryption.strategies.NonceStrategy
import io.ktor.util.network.*

@KordVoice
data class AudioFramePollerConfiguration(
    val server: NetworkAddress,
    val ssrc: UInt,
    val key: ByteArray,
    val provider: AudioProvider,
    val interceptorConfiguration: FrameInterceptorConfiguration,
    val nonceStrategy: NonceStrategy,
    val interceptor: FrameInterceptor,
)

@KordVoice
fun interface AudioFramePollerFactory {
    /**
     * Creates a new [AudioFramePoller] with the provided [VoiceContext]
     *
     * @param context The voice context
     */
    fun create(context: VoiceContext): AudioFramePoller
}

@KordVoice
interface AudioFramePoller {
    /**
     * This should start polling frames from [the audio provider][AudioFramePollerConfiguration.provider] and
     * send them to Discord.
     */
    suspend fun start(configuration: AudioFramePollerConfiguration)
}
