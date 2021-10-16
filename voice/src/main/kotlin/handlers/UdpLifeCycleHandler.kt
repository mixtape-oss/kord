@file:OptIn(KordVoice::class)

package dev.kord.voice.handlers

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.DefaultVoiceConnection
import dev.kord.voice.EncryptionMode
import dev.kord.voice.FrameInterceptorContextBuilder
import dev.kord.voice.gateway.*
import dev.kord.voice.udp.AudioFramePollerConfiguration
import io.ktor.util.network.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val udpLifeCycleLogger = KotlinLogging.logger { }

@OptIn(KordVoice::class)
internal class UdpLifeCycleHandler(
    flow: Flow<VoiceEvent>,
    private val connection: DefaultVoiceConnection
) : ConnectionEventHandler<VoiceEvent>(flow, "UdpInterceptor") {
    private var ssrc: UInt? by atomic(null)
    private var audioSenderJob: Job? by atomic(null)

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun start() = coroutineScope {
        on<Ready> {
            ssrc = it.ssrc
            connection.voiceServer = NetworkAddress(it.ip, it.port)

            val ip: NetworkAddress = connection.socket.discoverIp(connection.voiceServer!!, ssrc!!.toInt())
            udpLifeCycleLogger.trace { "ip discovered for voice successfully" }

            val selectProtocol = SelectProtocol(
                protocol = "udp",
                data = SelectProtocol.Data(
                    address = ip.hostname,
                    port = ip.port,
                    mode = EncryptionMode.XSalsa20Poly1305Lite
                )
            )

            connection.voiceGateway.send(selectProtocol)
        }

        on<SessionDescription> {
            with(connection) {
                val config = AudioFramePollerConfiguration(
                    ssrc = ssrc!!,
                    key = it.secretKey.toUByteArray().toByteArray(),
                    provider = audioProvider,
                    baseFrameInterceptorContext = FrameInterceptorContextBuilder(voiceGateway),
                    interceptorFactory = frameInterceptorFactory,
                    server = connection.voiceServer!!
                )

                audioSenderJob?.cancel()
                audioSenderJob = launch { framePoller.start(config) }
            }
        }

        on<Close> {
            audioSenderJob?.cancel()
            audioSenderJob = null
        }
    }
}
