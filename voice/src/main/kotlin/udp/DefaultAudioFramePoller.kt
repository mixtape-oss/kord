@file:OptIn(KordVoice::class)

package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceContext
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.random.Random

private val audioFrameSenderLogger = KotlinLogging.logger { }

object DefaultAudioFramePollerFactory : AudioFramePollerFactory {
    override fun create(context: VoiceContext): AudioFramePoller {
        return DefaultAudioFramePoller(context)
    }
}

@KordVoice
class DefaultAudioFramePoller(val data: VoiceContext) : AudioFramePoller {
    override suspend fun start(configuration: AudioFramePollerConfiguration): Unit = coroutineScope {
        var sequence: UShort = Random.nextBits(UShort.SIZE_BITS).toUShort()

        val packetProvider = DefaultAudioPackerProvider(configuration.key, configuration.nonceStrategy)

        val frames = Channel<AudioFrame?>(Channel.RENDEZVOUS)
        with(configuration.provider) { launch { provideFrames(frames) } }

        audioFrameSenderLogger.trace { "audio poller starting." }

        try {
            with(configuration.interceptor) {
                frames.consumeAsFlow()
                    .intercept(configuration.interceptorConfiguration)
                    .filterNotNull()
                    .map { packetProvider.provide(sequence, sequence * 960u, configuration.ssrc, it.data) }
                    .map { Datagram(ByteReadPacket(it.data, it.dataStart, it.viewSize), configuration.server) }
                    .onEach(data.udpSocket::send)
                    .onEach { sequence++ }
                    .collect()
            }
        } catch (e: Exception) {
            audioFrameSenderLogger.trace(e) { "poller stopped with reason" }
            /* we're done polling, nothing to worry about */
        }
    }
}
